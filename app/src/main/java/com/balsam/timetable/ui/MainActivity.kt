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
import com.balsam.timetable.Graph
import com.balsam.timetable.data.model.AppSettings
import com.balsam.timetable.data.model.ColorTheme
import com.balsam.timetable.ui.theme.TimetableTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

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
