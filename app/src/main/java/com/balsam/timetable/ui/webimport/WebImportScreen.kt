package com.balsam.timetable.ui.webimport

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.balsam.timetable.Graph
import com.balsam.timetable.data.html.WebImportBus
import com.balsam.timetable.data.repo.DEFAULT_WEB_IMPORT_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener

/**
 * 教务网导入：应用内 WebView 打开教务网（默认西南交大，可改为其他学校），
 * 用户手动登录并进入「我的选课记录」或「本学期周课表」页面后，
 * 点「确认导入」抓取当前页面 HTML 解析出课程，转交导入预览页确认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebImportScreen(
    onBack: () -> Unit,
    onParsed: () -> Unit,
) {
    var url by remember { mutableStateOf(DEFAULT_WEB_IMPORT_URL) }
    var urlLoaded by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var sslPrompt by remember { mutableStateOf<Pair<Int, SslErrorHandler>?>(null) }
    var consoleTail by remember { mutableStateOf(listOf<String>()) }
    var webHint by remember { mutableStateOf(false) }
    var editUrl by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 从设置读取持久化的教务网地址
    LaunchedEffect(Unit) {
        val saved = Graph.settingsRepository.webImportUrlOnce()
        url = saved
        if (!urlLoaded) {
            webView?.loadUrl(saved)
            urlLoaded = true
        }
    }

    fun grabAndParse() {
        val wv = webView ?: return
        busy = true
        error = null
        wv.evaluateJavascript(
            "(function(){return document.documentElement.outerHTML})()"
        ) { result ->
            scope.launch {
                try {
                    val html = withContext(Dispatchers.Default) {
                        val unquoted = JSONTokener(result).nextValue()
                        unquoted as? String ?: result
                    }
                    val entries = withContext(Dispatchers.IO) {
                        com.balsam.timetable.data.html.HtmlTimetableParser.parse(html)
                    }
                    WebImportBus.pendingEntries = entries
                    busy = false
                    onParsed()
                } catch (e: Exception) {
                    busy = false
                    error = e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("教务网导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新加载")
                    }
                    if (busy) {
                        CircularProgressIndicator(
                            Modifier.height(24.dp).padding(end = 12.dp)
                        )
                    } else {
                        Button(
                            onClick = { grabAndParse() },
                            modifier = Modifier.padding(end = 12.dp),
                        ) { Text("确认导入") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editUrl = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                "登录教务网后，打开「我的选课记录」或「本学期周课表」页面，再点右上角「确认导入」（页面显示空白不影响导入）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (webHint) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        "检测到学校网页的兼容性渲染问题，已自动尝试修复并刷新。" +
                            "若内容仍显示不全，不影响导入——直接点「确认导入」即可抓取课表数据，" +
                            "也可点右上角 ⟳ 手动刷新。",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            loadError?.let { le ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 4.dp)) {
                        Text(
                            le,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (consoleTail.isNotEmpty()) {
                            Text(
                                consoleTail.joinToString("\n"),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        TextButton(onClick = { webView?.reload() }) { Text("重试") }
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            // 教务网/校内系统常混用 http 资源与 http 子站，浏览器默认放行而 WebView 默认拦截
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            // 去掉 UA 中的 "wv" 标记，避免站点对 WebView 降级处理
                            settings.userAgentString = settings.userAgentString.replace("; wv)", "")
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                    val line = "[${consoleMessage.messageLevel()}] " +
                                        consoleMessage.message().take(160)
                                    if (consoleMessage.message().startsWith("kb")) webHint = true
                                    consoleTail = (consoleTail + line).takeLast(6)
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    error = null
                                    loadError = null
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    // 部分教务网页面用 viewport meta 禁用了双指缩放，这里放开
                                    view.evaluateJavascript(
                                        "(function(){var m=document.querySelector('meta[name=viewport]');" +
                                            "if(m){m.setAttribute('content','width=device-width, initial-scale=1, " +
                                            "maximum-scale=5, user-scalable=yes')}})()",
                                        null,
                                    )
                                    // 部分学校管理后台（如扬华学堂 /study）CSS 高度链有缺陷，
                                    // 内容区会塌陷成 0 只剩侧边栏；且实测该站对注入的样式表规则
                                    // 不生效，只有内联样式有效。SPA 渲染晚于 onPageFinished 且
                                    // 路由切换会产生新的塌陷容器，所以常驻轮询两层修复：
                                    // 1) 根链（html/body/#app/外壳）2) 内容区内所有"高度 0 但有内容"的容器；
                                    // 表格类组件会把初始化时的错误高度固化成内联样式，注入无效——
                                    // 检测到这种情况就自动刷新整页（实测刷新后组件按正确顺序
                                    // 初始化即可正常显示），sessionStorage 限次防止循环刷新
                                    view.evaluateJavascript(
                                        "(function(){if(window.__kbPatch)return;window.__kbPatch=1;" +
                                            "function setH(el,v,extra){if(!el)return;" +
                                            "el.style.setProperty('height',v,'important');" +
                                            "if(extra)el.style.setProperty('overflow-y','auto','important')}" +
                                            "var t=setInterval(function(){" +
                                            "var a=document.querySelector('#app');if(!a)return;" +
                                            "if(a.innerHTML.length>500&&a.getBoundingClientRect().height<50){" +
                                            "var vh=window.innerHeight+'px';" +
                                            "setH(document.documentElement,vh);setH(document.body,vh);" +
                                            "setH(a,vh);setH(document.querySelector('.app-wrapper'),vh);" +
                                            "setH(document.querySelector('.main-container'),vh,true);" +
                                            "setH(document.querySelector('.app-main'),vh);" +
                                            "console.log('kbPatch applied')}" +
                                            "var mc=document.querySelector('.main-container');" +
                                            "if(mc&&mc.getBoundingClientRect().height>50){" +
                                            "var els=mc.querySelectorAll('*');var n=0;var stuck=0;" +
                                            "for(var i=0;i<els.length;i++){var el=els[i];" +
                                            "if(el.dataset&&el.dataset.kbf){" +
                                            "if(el.getBoundingClientRect().height<2)stuck++;" +
                                            "continue}" +
                                            "var r=el.getBoundingClientRect();" +
                                            "if(r.height<2&&el.scrollHeight>150&&el.childElementCount>0){" +
                                            "el.style.setProperty('height','100%','important');" +
                                            "if(el.dataset)el.dataset.kbf=1;n++}}" +
                                            "if(n>0)console.log('kbDeepFix '+n);" +
                                            "if(stuck>0){var c=+(sessionStorage.getItem('kbR')||0);" +
                                            "if(c<2){sessionStorage.setItem('kbR',c+1);" +
                                            "console.log('kbReload');location.reload()}}}" +
                                            "},1200)})()",
                                        null,
                                    )
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    err: WebResourceError,
                                ) {
                                    if (request.isForMainFrame) {
                                        loadError = "页面加载失败：${err.description}。请检查网址与网络后重试。"
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView,
                                    handler: SslErrorHandler,
                                    error: SslError,
                                ) {
                                    // 校园内网系统证书常有异常：浏览器会弹"继续访问"，
                                    // WebView 默认直接失败，这里交给用户确认
                                    sslPrompt = error.primaryError to handler
                                }

                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResp: WebResourceResponse,
                                ) {
                                    if (request.isForMainFrame && errorResp.statusCode >= 400) {
                                        loadError = "教务网返回了错误（HTTP ${errorResp.statusCode}）。" +
                                            "多为学校服务器暂时故障，或需校园网/VPN 才能访问，可稍后重试。"
                                    }
                                }
                            }
                        }.also { webView = it }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    sslPrompt?.let { (errCode, handler) ->
        AlertDialog(
            onDismissRequest = {
                handler.cancel()
                sslPrompt = null
            },
            title = { Text("网站证书异常") },
            text = {
                Text(
                    "该网站的 HTTPS 证书存在问题（${sslErrorName(errCode)}），" +
                        "学校内网系统较常见。是否继续访问？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    handler.proceed()
                    sslPrompt = null
                }) { Text("继续访问") }
            },
            dismissButton = {
                TextButton(onClick = {
                    handler.cancel()
                    sslPrompt = null
                }) { Text("取消") }
            },
        )
    }

    if (editUrl) {
        var edited by remember { mutableStateOf(url) }
        AlertDialog(
            onDismissRequest = { editUrl = false },
            title = { Text("教务网网址") },
            text = {
                OutlinedTextField(
                    value = edited,
                    onValueChange = { edited = it },
                    singleLine = true,
                    label = { Text("支持任意学校的教务网地址") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = normalizeUrl(edited)
                    url = u
                    urlLoaded = true
                    webView?.loadUrl(u)
                    editUrl = false
                    scope.launch { Graph.settingsRepository.setWebImportUrl(u) }
                }) { Text("确定并打开") }
            },
            dismissButton = {
                TextButton(onClick = {
                    edited = DEFAULT_WEB_IMPORT_URL
                }) { Text("恢复默认") }
            },
        )
    }
}

private fun normalizeUrl(u: String): String {
    val t = u.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://") -> t
        t.isEmpty() -> DEFAULT_WEB_IMPORT_URL
        else -> "https://$t"
    }
}

private fun sslErrorName(code: Int): String = when (code) {
    SslError.SSL_NOTYETVALID -> "证书尚未生效"
    SslError.SSL_EXPIRED -> "证书已过期"
    SslError.SSL_IDMISMATCH -> "证书域名不匹配"
    SslError.SSL_UNTRUSTED -> "证书不受信任"
    SslError.SSL_DATE_INVALID -> "证书日期无效"
    else -> "证书无效"
}
