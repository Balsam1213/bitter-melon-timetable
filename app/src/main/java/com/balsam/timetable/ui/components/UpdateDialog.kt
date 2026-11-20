package com.balsam.timetable.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.balsam.timetable.BuildConfig
import com.balsam.timetable.data.update.UpdateFound

/**
 * 「发现新版本」对话框：三动作（下载安装包 / 查看更新说明 / 忽略此版本），
 * 启动自动检查与设置页手动检查共用。下载不内置 APK 下载器——
 * 交给系统浏览器打开 GitHub Releases 直链（隐私：不经过应用内任何服务器中转）。
 */
@Composable
fun UpdateFoundDialog(
    found: UpdateFound,
    /** 点「忽略此版本」：调用方负责记录忽略的 tag 并关闭弹窗 */
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showNotes by remember { mutableStateOf(false) }
    val version = found.tagName.removePrefix("v")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 $version") },
        text = {
            if (showNotes) {
                Text(
                    found.body.ifBlank { "本次更新没有说明文字。" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                )
            } else {
                Text(
                    "当前版本 v${BuildConfig.VERSION_NAME}。" +
                        "安装包按你的设备自动从 GitHub Releases 选择（x86 未发布时给通用包）。",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(found.downloadUrl))) }
            }) { Text("下载安装包") }
        },
        dismissButton = {
            TextButton(onClick = { showNotes = !showNotes }) {
                Text(if (showNotes) "收起说明" else "查看更新说明")
            }
            TextButton(onClick = onIgnore) { Text("忽略此版本") }
        },
    )
}
