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
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.balsam.timetable.Graph
import com.balsam.timetable.ui.theme.TimetableTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimetableTheme {
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
