package com.balsam.timetable.ui.importflow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.importer.CsvImporter
import com.balsam.timetable.data.importer.ExportDoc
import com.balsam.timetable.data.importer.ImportPlanner
import com.balsam.timetable.data.importer.JsonImporter
import com.balsam.timetable.data.importer.OcrParser
import com.balsam.timetable.data.importer.ParsedEntry
import com.balsam.timetable.data.importer.RowMapper
import com.balsam.timetable.data.importer.TextParsers
import com.balsam.timetable.data.importer.XlsxParser
import com.balsam.timetable.data.importer.friendlyParseError
import com.balsam.timetable.data.model.WeekLogic
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImportViewModel : ViewModel() {

    private val repo = Graph.repository

    /** true = 从「新建学期」流程进入，导入必须落到当前学期（忽略文件内嵌的学期元数据） */
    var freshSemester: Boolean = false

    /** 导入前临时确认的学期信息：无学期时先让用户核对，避免静默用「本周一」当开学日 */
    data class SemesterDraft(
        val name: String,
        val startDate: String,
        val totalWeeks: Int,
    )

    data class UiState(
        val busy: Boolean = false,
        val sourceLabel: String? = null,
        val entries: List<ParsedEntry> = emptyList(),
        val jsonDoc: ExportDoc? = null,
        val error: String? = null,
        val importResult: com.balsam.timetable.data.importer.ImportResult? = null,
        val pendingNewSemester: SemesterDraft? = null,
        /** 目标学期周数（JSON 取内嵌值，其余取当前学期或 20），预览页逐条校验用 */
        val totalWeeks: Int = 20,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    fun reset() {
        _ui.value = UiState()
    }

    fun parseFileFromUri(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    Graph.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) {
                _ui.value = _ui.value.copy(error = "无法读取所选文件")
                return@launch
            }
            parseFile(bytes, queryDisplayName(uri) ?: "import")
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        Graph.appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    /** 装载预览条目：无效条目默认不勾选（预览页逐条标注原因，用户可改字段后手动勾选） */
    private fun loadedEntries(entries: List<ParsedEntry>, totalWeeks: Int): List<ParsedEntry> =
        entries.map { if (ImportPlanner.validate(it, totalWeeks) != null) it.copy(include = false) else it }

    private suspend fun targetTotalWeeks(): Int = Graph.repository.currentSemesterOnce()?.totalWeeks ?: 20

    fun parseFile(bytes: ByteArray, fileName: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, importResult = null)
            try {
                val lower = fileName.lowercase()
                when {
                    lower.endsWith(".json") -> {
                        val doc = JsonImporter.parse(TextParsers.decodeBytes(bytes))
                        check(doc.entries.isNotEmpty()) { "备份文件里没有课程条目" }
                        _ui.value = UiState(
                            sourceLabel = "JSON 文件",
                            jsonDoc = doc,
                            entries = loadedEntries(doc.entries.map { it.toParsedEntry() }, doc.semester.totalWeeks),
                            totalWeeks = doc.semester.totalWeeks,
                        )
                    }
                    lower.endsWith(".xlsx") -> {
                        val entries = withContext(Dispatchers.Default) {
                            RowMapper.fromRows(XlsxParser.parse(bytes))
                        }
                        val weeks = targetTotalWeeks()
                        _ui.value = UiState(
                            sourceLabel = "Excel 文件",
                            entries = loadedEntries(entries, weeks),
                            totalWeeks = weeks,
                        )
                        check(entries.isNotEmpty()) { "没有解析出课程，请按模板格式填写（列：星期、节次、课程…）" }
                    }
                    lower.endsWith(".xls") -> {
                        throw IllegalArgumentException("旧版 .xls 不支持，请在 Office / WPS 中另存为 .xlsx")
                    }
                    else -> {
                        val entries = CsvImporter.parse(bytes)
                        val weeks = targetTotalWeeks()
                        _ui.value = UiState(
                            sourceLabel = "CSV 文件",
                            entries = loadedEntries(entries, weeks),
                            totalWeeks = weeks,
                        )
                        check(entries.isNotEmpty()) { "没有解析出课程，请按模板格式填写（列：星期、节次、课程…）" }
                    }
                }
            } catch (e: Exception) {
                _ui.value = UiState(error = "解析失败：${friendlyParseError(e)}")
            }
        }
    }

    fun parseImage(uri: Uri) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, importResult = null)
            try {
                val entries = withContext(Dispatchers.IO) {
                    val bmp = decodeSampled(uri)
                        ?: throw IllegalArgumentException("无法读取所选图片")
                    // 注意：不在 await 后立刻 recycle——协程被取消时 ML Kit 可能仍在
                    // 读取位图（曾引发混淆栈 NPE），交给 GC 回收；识别器用完即关
                    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                    try {
                        val text = recognize(recognizer, bmp)
                        OcrParser.parse(text)
                    } finally {
                        recognizer.close()
                    }
                }
                // 识别默认周次跟随当前学期总周数（无学期时按自动新建的 20 周）
                val totalWeeks = targetTotalWeeks()
                val adjusted = entries.map { e ->
                    if (e.weeks == (1..16).toSet()) {
                        e.copy(weeks = (1..totalWeeks).toSet())
                    } else {
                        e
                    }
                }
                _ui.value = UiState(
                    sourceLabel = "图片识别",
                    entries = loadedEntries(adjusted, totalWeeks),
                    totalWeeks = totalWeeks,
                )
                check(adjusted.isNotEmpty()) { "没有识别出课程，请换一张更清晰的课表截图" }
            } catch (e: OcrParser.OcrParseException) {
                _ui.value = UiState(error = e.message)
            } catch (e: Exception) {
                _ui.value = UiState(error = "识别失败：${friendlyParseError(e)}")
            }
        }
    }

    fun toggleInclude(index: Int) {
        val s = _ui.value
        val updated = s.entries.mapIndexed { i, e -> if (i == index) e.copy(include = !e.include) else e }
        _ui.value = s.copy(entries = updated)
    }

    fun updateEntry(index: Int, entry: ParsedEntry) {
        val s = _ui.value
        _ui.value = s.copy(entries = s.entries.mapIndexed { i, e -> if (i == index) entry else e })
    }

    /** 教务网导入等外部来源：直接装载解析结果进入预览 */
    fun loadExternal(entries: List<com.balsam.timetable.data.importer.ParsedEntry>) {
        viewModelScope.launch {
            val weeks = targetTotalWeeks()
            _ui.value = UiState(
                sourceLabel = "教务网导入",
                entries = loadedEntries(entries, weeks),
                totalWeeks = weeks,
            )
        }
    }

    fun confirmImport() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                val doc = _ui.value.jsonDoc
                val result = if (doc != null && !freshSemester) {
                    // 常规导入：按文件内嵌的学期信息合并/新建学期
                    repo.importFromDoc(doc)
                } else if (doc != null && freshSemester) {
                    // 新建学期流程：强制导入当前学期（忽略文件内嵌学期元数据）
                    val sem = repo.currentSemesterOnce()
                        ?: throw IllegalStateException("没有当前学期")
                    if (doc.slots.isNotEmpty() && repo.slotsOnce(sem.id).isEmpty()) {
                        repo.replaceSlots(
                            sem.id,
                            doc.slots.map { it.start to it.end },
                        )
                    }
                    repo.importEntries(
                        sem.id,
                        doc.entries.map { it.toParsedEntry() },
                        clearExisting = false,
                    )
                } else {
                    var sem = repo.currentSemesterOnce()
                    if (sem == null) {
                        // 首次导入没有学期：弹窗让用户核对学期信息后再建，
                        // 不再静默用「本周一」当开学日（那会让周次与提醒全错位）
                        _ui.value = _ui.value.copy(
                            busy = false,
                            pendingNewSemester = SemesterDraft(
                                name = semesterNameSuggestion(LocalDate.now()),
                                startDate = WeekLogic.mondayOf(LocalDate.now()).toString(),
                                totalWeeks = 20,
                            ),
                        )
                        return@launch
                    }
                    repo.importEntries(
                        sem.id,
                        _ui.value.entries,
                        clearExisting = false,
                    )
                }
                _ui.value = _ui.value.copy(busy = false, importResult = result)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, error = "导入失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 用户在「先建个学期」弹窗里确认后：建学期并继续导入所选条目 */
    fun confirmNewSemesterAndImport(draft: SemesterDraft) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, pendingNewSemester = null)
            try {
                repo.createSemester(draft.name.trim(), draft.startDate, draft.totalWeeks, setCurrent = true)
                val sem = repo.currentSemesterOnce() ?: throw IllegalStateException("学期创建失败")
                val result = repo.importEntries(sem.id, _ui.value.entries, clearExisting = false)
                _ui.value = _ui.value.copy(busy = false, importResult = result)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, error = "导入失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 取消「先建个学期」弹窗，回到预览页（条目保留，可改完再导） */
    fun dismissNewSemester() {
        _ui.value = _ui.value.copy(pendingNewSemester = null)
    }

    private fun decodeSampled(uri: Uri, maxDim: Int = 2048): Bitmap? {
        val cr = Graph.appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w / 2 >= maxDim || h / 2 >= maxDim) {
            w /= 2; h /= 2; sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        // 手机保存的 JPG 常带 EXIF 旋转标记，BitmapFactory 不处理，需手动转正
        val rotated = runCatching { applyExifRotation(cr, uri, bmp) }.getOrNull()
        return rotated ?: bmp
    }

    private fun applyExifRotation(cr: android.content.ContentResolver, uri: Uri, bmp: Bitmap): Bitmap? {
        val orientation = cr.openInputStream(uri)?.use { stream ->
            androidx.exifinterface.media.ExifInterface(stream).getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return null
        val m = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private suspend fun recognize(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bmp: Bitmap,
    ): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { t -> if (cont.isActive) cont.resume(t) }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

private fun semesterNameSuggestion(today: LocalDate): String {
    val year = today.year
    val month = today.monthValue
    val academicYear = if (month >= 8) "$year-${year + 1}" else "${year - 1}-$year"
    val term = if (month in 2..7) "第二学期" else "第一学期"
    return "${academicYear}学年 $term"
}
}
