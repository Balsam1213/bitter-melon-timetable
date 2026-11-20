package com.balsam.timetable.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balsam.timetable.BuildConfig
import com.balsam.timetable.Graph
import com.balsam.timetable.data.model.AppSettings
import com.balsam.timetable.data.model.ColorTheme
import com.balsam.timetable.data.update.UpdateChecker
import com.balsam.timetable.data.update.UpdateFound
import com.balsam.timetable.ui.components.UpdateFoundDialog
import com.balsam.timetable.ui.theme.TimetableTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            // 首帧用默认主题，DataStore 加载后切换（应用内一次闪变可接受）
            TimetableTheme(colorTheme = settings?.colorTheme ?: ColorTheme.PURPLE) {
                RequestNotificationPermission()
                AppNavHost()
                // 启动自动检查更新（默认关，开启后每天限一次）：发现新版且未忽略才弹
                val startupUpdate by vm.startupUpdate.collectAsStateWithLifecycle()
                startupUpdate?.let { found ->
                    UpdateFoundDialog(
                        found = found,
                        onIgnore = { vm.ignoreStartupUpdate() },
                        onDismiss = { vm.dismissStartupUpdate() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Graph.refreshRemindersAndWidget()
    }
}

/** activity 级：主题在导航最外层应用，不随页面销毁重建 */
class MainViewModel : ViewModel() {
    val settings: StateFlow<AppSettings?> = Graph.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 启动自动检查更新；发现新版且未忽略时非空，弹窗在 MainActivity 渲染 */
    private val _startupUpdate = MutableStateFlow<UpdateFound?>(null)
    val startupUpdate: StateFlow<UpdateFound?> = _startupUpdate.asStateFlow()

    init {
        viewModelScope.launch {
            val repo = Graph.settingsRepository
            val st = repo.once()
            val today = java.time.LocalDate.now().toEpochDay()
            // 自动检查默认关；开启后每天限一次匿名检查（成败都记，弱网不反复打 API）
            if (!UpdateChecker.shouldAutoCheck(st.updateAutoCheck, repo.updateLastCheckDay(), today)) return@launch
            repo.markUpdateChecked(today)
            val found = withContext(Dispatchers.IO) {
                UpdateChecker.fetchLatest(Build.SUPPORTED_ABIS.toList())
            } ?: return@launch
            if (!UpdateChecker.isUpdateAvailable(BuildConfig.VERSION_NAME, found.tagName)) return@launch
            if (found.tagName == repo.updateIgnored()) return@launch
            _startupUpdate.value = found
        }
    }

    fun dismissStartupUpdate() {
        _startupUpdate.value = null
    }

    /** 忽略此版本：记录 tag（此后自动检查不再弹该版），手动检查不受影响 */
    fun ignoreStartupUpdate() {
        val found = _startupUpdate.value ?: return
        _startupUpdate.value = null
        viewModelScope.launch { Graph.settingsRepository.setUpdateIgnored(found.tagName) }
    }
}

/** Android 13+ 首次启动请求通知权限（拒绝过就不再骚扰，可在设置页引导） */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!granted && !Graph.settingsRepository.notificationAsked()) {
            Graph.settingsRepository.markNotificationAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
