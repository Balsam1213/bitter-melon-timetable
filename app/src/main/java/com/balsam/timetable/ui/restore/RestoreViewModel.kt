package com.balsam.timetable.ui.restore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.backup.AutoBackup
import com.balsam.timetable.data.backup.BackupValidator
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.importer.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RestoreViewModel : ViewModel() {

    private val repo = Graph.repository

    /** 合并恢复 = 备份课程并入目标学期；完整恢复 = 备份整体替换目标学期（先自动快照） */
    enum class Mode { MERGE, FULL }

    data class UiState(
        val loading: Boolean = true,
        val backups: List<AutoBackup.BackupMeta> = emptyList(),
        /** 当前选中进入预览的备份；null = 列表页 */
        val selected: AutoBackup.BackupMeta? = null,
        val semesters: List<Semester> = emptyList(),
        val targetId: Long? = null,
        val mode: Mode = Mode.MERGE,
        val busy: Boolean = false,
        val error: String? = null,
        val result: ImportResult? = null,
        /** 不打断流程的轻提示 */
        val info: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init {
        refresh()
    }

    /** 扫描备份目录 + 目标学期列表（进入页面与返回列表时调用） */
    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val metas = AutoBackup.listBackupMetas(Graph.appContext)
            val semesters = repo.semesters().first().sortedWith(
                compareByDescending<Semester> { it.isCurrent }.thenByDescending { it.startDate },
            )
            val current = semesters.firstOrNull { it.isCurrent } ?: semesters.firstOrNull()
            // copy 而非整包替换：init 的异步扫描若晚于 select/pickFile 完成，不能把已选中的备份冲掉
            _ui.value = _ui.value.copy(
                loading = false,
                backups = metas,
                semesters = semesters,
                targetId = _ui.value.targetId ?: current?.id,
            )
        }
    }

    /** 点选备份文件 → 进入恢复预览（数据已在扫描时校验过） */
    fun select(meta: AutoBackup.BackupMeta) {
        if (meta.doc == null) {
            _ui.value = _ui.value.copy(error = meta.parseError ?: "备份文件无效")
            return
        }
        _ui.value = _ui.value.copy(selected = meta, error = null, result = null, info = null)
    }

    /** 从任意位置选择 JSON 文件（跨设备场景：微信/网盘传来的备份） */
    fun pickFile(uri: Uri) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, result = null)
            try {
                val text = withContext(Dispatchers.IO) {
                    Graph.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?.toString(Charsets.UTF_8)
                } ?: throw IllegalArgumentException("无法读取所选文件")
                val doc = BackupValidator.parseAndValidate(text)
                val meta = AutoBackup.BackupMeta(
                    fileName = queryDisplayName(uri) ?: "所选文件",
                    timestampMillis = System.currentTimeMillis(),
                    sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(),
                    doc = doc,
                    parseError = null,
                )
                _ui.value = _ui.value.copy(busy = false, selected = meta)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, error = e.message ?: "备份文件无效")
            }
        }
    }

    fun setTarget(id: Long) {
        _ui.value = _ui.value.copy(targetId = id)
    }

    fun setMode(mode: Mode) {
        _ui.value = _ui.value.copy(mode = mode)
    }

    fun backToList() {
        _ui.value = _ui.value.copy(selected = null, result = null, error = null, info = null)
        refresh()
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }

    /**
     * 执行恢复。完整恢复先落快照（失败则中止，不动数据库），再在单个事务里替换；
     * 合并恢复直接进事务。失败时事务回滚，原有数据分毫不动。
     * 没有任何学期时（新装直接恢复）：先按备份信息创建学期再恢复，无需快照。
     */
    fun confirmRestore() {
        val state = _ui.value
        val meta = state.selected ?: return
        val doc = meta.doc ?: return
        val full = state.mode == Mode.FULL
        viewModelScope.launch {
            _ui.value = state.copy(busy = true, error = null, info = null, result = null)
            try {
                var targetId = state.targetId
                var createdNew = false
                if (targetId == null) {
                    targetId = repo.createSemester(
                        name = doc.semester.name,
                        startDate = doc.semester.startDate,
                        totalWeeks = doc.semester.totalWeeks,
                        setCurrent = true,
                        slots = doc.slots.map { it.start to it.end },
                    )
                    createdNew = true
                    _ui.value = _ui.value.copy(info = "已按备份创建学期「${doc.semester.name}」")
                }
                if (full && !createdNew) {
                    val targetName = state.semesters.firstOrNull { it.id == targetId }?.name ?: "当前学期"
                    // 快照失败必须中止：不能在没有退路的情况下清空用户数据
                    val snapshotOk = withContext(Dispatchers.IO) {
                        val currentDoc = repo.exportDoc(targetId)
                            ?: throw IllegalStateException("读取目标学期当前数据失败")
                        AutoBackup.writeSnapshot(Graph.appContext, targetName, AutoBackup.encodeDoc(currentDoc))
                    }
                    if (!snapshotOk) {
                        throw IllegalStateException(
                            "自动快照创建失败（检查存储空间），已中止恢复。" +
                                "也可以先用「导出当前学期为 JSON」手动备份，再重试完整恢复",
                        )
                    }
                    _ui.value = _ui.value.copy(info = "已创建恢复前快照，写入「文档 / 苦瓜课表」")
                }
                val result = repo.restoreDoc(targetId, doc, replaceAll = full)
                _ui.value = _ui.value.copy(busy = false, result = result)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = "恢复失败：${e.message ?: e.javaClass.simpleName}（原有数据未受影响）",
                )
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        Graph.appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
}
