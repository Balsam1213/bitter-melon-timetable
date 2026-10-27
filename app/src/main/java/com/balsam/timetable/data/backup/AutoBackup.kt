package com.balsam.timetable.data.backup

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.balsam.timetable.Graph
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 本机自动备份：把当前学期导出为 JSON 存进 Documents/苦瓜课表/，保留最近 7 份。
 * API 29+ 走 MediaStore（免权限、用户可见）；26–28 退化到应用外部私有目录（免权限，
 * 但卸载即清，仅作兜底）。备份是纯本地动作，不联网不上传。
 */
object AutoBackup {

    const val KEEP_COUNT = 7
    private val DIR_NAME = "苦瓜课表"
    private val NAME_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** 备份结果：NoSemester = 没有当前学期（新装机属正常，不算失败、不写状态，避免设置页误报红字） */
    enum class BackupOutcome { Success, NoSemester, Failed }

    /** 备份当前学期；成功与失败都会更新设置页的状态时间戳 */
    suspend fun backupNow(context: Context): BackupOutcome {
        val semester = Graph.repository.currentSemesterOnce() ?: return BackupOutcome.NoSemester
        val doc = Graph.repository.exportDoc(semester.id) ?: return BackupOutcome.NoSemester
        val json = runCatching {
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }.encodeToString(com.balsam.timetable.data.importer.ExportDoc.serializer(), doc)
        }.getOrElse { return BackupOutcome.Failed }

        val name = "课程表备份-${LocalDateTime.now().format(NAME_DATE)}.json"
        val ok = if (Build.VERSION.SDK_INT >= 29) {
            writeViaMediaStore(context, name, json.toByteArray(Charsets.UTF_8))
        } else {
            writeAppExternal(context, name, json.toByteArray(Charsets.UTF_8))
        }
        return if (ok) {
            trimOldBackups(context)
            Graph.settingsRepository.markBackupDone()
            BackupOutcome.Success
        } else {
            Graph.settingsRepository.markBackupFailed()
            BackupOutcome.Failed
        }
    }

    /** Documents/苦瓜课表/ 下的备份文件名列表（新→旧），两个存储通道统一视角 */
    fun listBackups(context: Context): List<String> {
        if (Build.VERSION.SDK_INT >= 29) {
            val out = mutableListOf<Pair<String, Long>>()
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.DATE_ADDED),
                "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND ${MediaStore.Files.FileColumns.MIME_TYPE}=?",
                arrayOf("${Environment.DIRECTORY_DOCUMENTS}/$DIR_NAME/", "application/json"),
                null,
            )?.use { c ->
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                while (c.moveToNext()) out.add(c.getString(nameIdx) to c.getLong(dateIdx))
            }
            return out.sortedByDescending { it.second }.map { it.first }
        }
        return backupDir(context)?.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name }
            ?: emptyList()
    }

    private fun writeViaMediaStore(context: Context, name: String, bytes: ByteArray): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$DIR_NAME")
        }
        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching false
        true
    }.getOrDefault(false)

    private fun writeAppExternal(context: Context, name: String, bytes: ByteArray): Boolean {
        val dir = backupDir(context) ?: return false
        return runCatching {
            File(dir, name).writeBytes(bytes)
        }.isSuccess
    }

    private fun backupDir(context: Context): File? {
        val dir = File(context.getExternalFilesDir(null), "backups")
        return if (dir.isDirectory || dir.mkdirs()) dir else null
    }

    /** 只保留最近 KEEP_COUNT 份，旧备份删除 */
    private fun trimOldBackups(context: Context) {
        if (Build.VERSION.SDK_INT >= 29) {
            val uri = MediaStore.Files.getContentUri("external")
            val selection =
                "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND ${MediaStore.Files.FileColumns.MIME_TYPE}=?"
            val args = arrayOf("${Environment.DIRECTORY_DOCUMENTS}/$DIR_NAME/", "application/json")
            val ids = mutableListOf<Long>()
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Files.FileColumns._ID),
                selection, args,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) ids.add(c.getLong(0))
            }
            ids.drop(KEEP_COUNT).forEach { id ->
                context.contentResolver.delete(uri, "${MediaStore.Files.FileColumns._ID}=?", arrayOf(id.toString()))
            }
            return
        }
        val files = backupDir(context)?.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(KEEP_COUNT).forEach { it.delete() }
    }

    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(Duration.ofDays(1)).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 设置页「立即备份」：插队执行一次，不等每日周期 */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            BACKUP_NOW_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().build(),
        )
    }

    /** 备份时间戳转显示文案（设置页用） */
    fun describeTime(epochMillis: Long): String {
        val t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
        val today = LocalDate.now()
        return when (t.toLocalDate()) {
            today -> "今天 ${t.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            today.minusDays(1) -> "昨天 ${t.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            else -> t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }
    }

    private const val WORK_NAME = "auto_backup"
    private const val BACKUP_NOW_NAME = "backup_now"
}

/** 每日一次的周期备份；Graph 已在 Application 初始化后才可能执行 */
class AutoBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (AutoBackup.backupNow(applicationContext)) {
        AutoBackup.BackupOutcome.Success, AutoBackup.BackupOutcome.NoSemester -> Result.success()
        AutoBackup.BackupOutcome.Failed -> Result.retry()
    }
}
