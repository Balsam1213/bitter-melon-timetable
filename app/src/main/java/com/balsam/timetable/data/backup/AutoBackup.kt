package com.balsam.timetable.data.backup

import android.content.ContentUris
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
import com.balsam.timetable.data.importer.ExportDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val json = runCatching { encodeDoc(doc) }.getOrElse { return BackupOutcome.Failed }

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

    /** Documents/苦瓜课表/ 下的备份文件（新→旧），两个存储通道统一视角 */
    data class BackupEntry(
        val fileName: String,
        val timestampMillis: Long,
        val sizeBytes: Long,
    )

    /** 备份文件名列表（新→旧），两个存储通道统一视角 */
    fun listBackups(context: Context): List<BackupEntry> {
        if (Build.VERSION.SDK_INT >= 29) {
            val out = mutableListOf<Triple<String, Long, Long>>()
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.SIZE,
                ),
                "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND ${MediaStore.Files.FileColumns.MIME_TYPE}=?",
                arrayOf("${Environment.DIRECTORY_DOCUMENTS}/$DIR_NAME/", "application/json"),
                null,
            )?.use { c ->
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                while (c.moveToNext()) {
                    out.add(Triple(c.getString(nameIdx), c.getLong(dateIdx) * 1000, c.getLong(sizeIdx)))
                }
            }
            return out.sortedByDescending { it.second }
                .map { BackupEntry(it.first, it.second, it.third) }
        }
        return backupDir(context)?.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { BackupEntry(it.name, it.lastModified(), it.length()) }
            ?: emptyList()
    }

    /** 恢复页列表项：备份文件 + 解析出的摘要（解析失败时 doc 为 null、parseError 说明原因） */
    data class BackupMeta(
        val fileName: String,
        val timestampMillis: Long,
        val sizeBytes: Long,
        val doc: ExportDoc?,
        val parseError: String?,
    )

    /** 列出并解析全部备份（读文件 + JSON 校验，IO 耗时；调用方放在后台协程里） */
    suspend fun listBackupMetas(context: Context): List<BackupMeta> = withContext(Dispatchers.IO) {
        listBackups(context).map { entry ->
            val text = readBackup(context, entry.fileName)
            val result = text?.let { runCatching { BackupValidator.parseAndValidate(it) } }
            BackupMeta(
                fileName = entry.fileName,
                timestampMillis = entry.timestampMillis,
                sizeBytes = entry.sizeBytes,
                doc = result?.getOrNull(),
                parseError = when {
                    text == null -> "无法读取文件内容"
                    result == null -> "未知错误"
                    result.isFailure -> result.exceptionOrNull()?.message ?: "备份格式无效"
                    else -> null
                },
            )
        }
    }

    /** 读取指定备份文件的 JSON 文本；读不到或读失败返回 null */
    fun readBackup(context: Context, fileName: String): String? {
        return if (Build.VERSION.SDK_INT >= 29) {
            val baseUri = MediaStore.Files.getContentUri("external")
            val id = context.contentResolver.query(
                baseUri,
                arrayOf(MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME}=?",
                arrayOf("${Environment.DIRECTORY_DOCUMENTS}/$DIR_NAME/", fileName),
                null,
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return null
            runCatching {
                context.contentResolver
                    .openInputStream(ContentUris.withAppendedId(baseUri, id))
                    ?.use { it.readBytes() }
                    ?.toString(Charsets.UTF_8)
            }.getOrNull()
        } else {
            val f = File(backupDir(context), fileName)
            if (!f.isFile) return null
            runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
        }
    }

    /**
     * 完整恢复前的自动快照：把目标学期当前数据写进同一备份目录，
     * 命名与普通备份区分（恢复前快照-…）。返回 false 时调用方应中止恢复。
     */
    fun writeSnapshot(context: Context, semesterName: String, json: String): Boolean {
        val safeName = semesterName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).ifBlank { "学期" }
        val name = "恢复前快照-$safeName-${LocalDateTime.now().format(NAME_DATE)}.json"
        val bytes = json.toByteArray(Charsets.UTF_8)
        return if (Build.VERSION.SDK_INT >= 29) {
            writeViaMediaStore(context, name, bytes)
        } else {
            writeAppExternal(context, name, bytes)
        }
    }

    /** ExportDoc → 格式化 JSON（每日备份与恢复前快照共用一份序列化配置） */
    fun encodeDoc(doc: ExportDoc): String = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }.encodeToString(ExportDoc.serializer(), doc)

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
