package com.balsam.timetable.ui.webimport

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.DisposableEffect
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
 *
 * 安全边界：只放行 http/https；明文 http 与跳转站外都要用户确认；
 * 证书异常按站点记忆本会话的选择。这些决定只在本页面存活，退出即失效。
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
    var sslPrompt by remember { mutableStateOf<SslPrompt?>(null) }
    var consoleTail by remember { mutableStateOf(listOf<String>()) }
    var webHint by remember { mutableStateOf(false) }
    var editUrl by remember { mutableStateOf(false) }
    // 明文 http 加载前确认；target 为被拦下的完整地址
    var pendingHttpLoad by remember { mutableStateOf<String?>(null) }
    // 跳转到其他网站前确认；target 为站外地址
    var offSitePrompt by remember { mutableStateOf<String?>(null) }
    // 会话级记忆：证书放行/拒绝按 host，明文 http 按 host，站外域名按注册域——避免子资源错误反复弹窗
    var sslApproved by remember { mutableStateOf(setOf<String>()) }
    var sslDenied by remember { mutableStateOf(setOf<String>()) }
    var httpApproved by remember { mutableStateOf(setOf<String>()) }
    var approvedDomains by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    // 从设置读取持久化的教务网地址；旧版本可能存进过 file:// 等非法值，读出时重新校验
    LaunchedEffect(Unit) {
        val saved = normalizeUrl(Graph.settingsRepository.webImportUrlOnce()) ?: DEFAULT_WEB_IMPORT_URL
        url = saved
        registrableDomain(Uri.parse(saved).host)?.let { approvedDomains = approvedDomains + it }
        if (!urlLoaded) {
            webView?.loadUrl(saved)
            urlLoaded = true
        }
    }

    // 离开页面时把登录态落盘，下次进来不用重新登录
    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().flush() }
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
                            // 教务网页面用不到本地文件与 content:// 资源，关掉减少攻击面
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
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
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    if (!request.isForMainFrame) return false
                                    val target = request.url
                                    val scheme = target.scheme?.lowercase()
                                    if (scheme == "http" || scheme == "https") {
                                        // 同注册域自动放行（高校 SSO 常跨子域跳转），站外交用户确认
                                        val dom = registrableDomain(target.host)
                                        if (dom != null && dom in approvedDomains) return false
                                        offSitePrompt = target.toString()
                                        return true
                                    }
                                    // tel:/mailto:/intent: 等交给系统应用处理
                                    runCatching {
                                        view.context.startActivity(Intent(Intent.ACTION_VIEW, target))
                                    }
                                    return true
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    error = null
                                    loadError = null
                                    // 服务端重定向不经过 shouldOverrideUrlLoading，明文拦截要在这一层兜底
                                    val u = url?.let { Uri.parse(it) }
                                    if (u != null && u.scheme?.lowercase() == "http" &&
                                        u.host != null && u.host !in httpApproved
                                    ) {
                                        view?.stopLoading()
                                        pendingHttpLoad = url
                                    }
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
                                    // WebView 默认直接失败，这里交给用户确认。
                                    // 同一 host 的子资源错误会触发多次，按 host 记住本会话的选择
                                    val host = Uri.parse(view.url ?: "").host
                                    if (host == null) {
                                        handler.cancel()
                                        return
                                    }
                                    when {
                                        host in sslApproved -> handler.proceed()
                                        host in sslDenied -> handler.cancel()
                                        else -> sslPrompt = SslPrompt(error.primaryError, handler, host)
                                    }
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

    sslPrompt?.let { p ->
        AlertDialog(
            onDismissRequest = {
                p.handler.cancel()
                sslDenied = sslDenied + p.host
                sslPrompt = null
            },
            title = { Text("网站证书异常") },
            text = {
                Text(
                    "该网站的 HTTPS 证书存在问题（${sslErrorName(p.errCode)}），" +
                        "学校内网系统较常见。继续访问时连接不受验证，" +
                        "若在此网站输入学号密码，凭据可能被窃听。是否继续？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    p.handler.proceed()
                    sslApproved = sslApproved + p.host
                    sslPrompt = null
                }) { Text("继续访问") }
            },
            dismissButton = {
                TextButton(onClick = {
                    p.handler.cancel()
                    sslDenied = sslDenied + p.host
                    sslPrompt = null
                }) { Text("取消") }
            },
        )
    }

    pendingHttpLoad?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingHttpLoad = null },
            title = { Text("不加密的网站") },
            text = {
                Text(
                    "该网站使用不加密的 HTTP，页面内容和登录凭据都可能被窃听或篡改。" +
                        "建议改用学校网站的 https 地址。是否仍要访问？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Uri.parse(target).host?.let { httpApproved = httpApproved + it }
                    pendingHttpLoad = null
                    webView?.loadUrl(target)
                }) { Text("仍要访问") }
            },
            dismissButton = {
                TextButton(onClick = { pendingHttpLoad = null }) { Text("取消") }
            },
        )
    }

    offSitePrompt?.let { target ->
        AlertDialog(
            onDismissRequest = { offSitePrompt = null },
            title = { Text("即将离开教务网站点") },
            text = {
                Text(
                    "该链接指向其他网站（${Uri.parse(target).host ?: "未知来源"}）。" +
                        "课表数据只应从教务网抓取，来源不明的页面不要输入学号密码。是否继续打开？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    registrableDomain(Uri.parse(target).host)?.let { approvedDomains = approvedDomains + it }
                    offSitePrompt = null
                    webView?.loadUrl(target)
                }) { Text("继续打开") }
            },
            dismissButton = {
                TextButton(onClick = { offSitePrompt = null }) { Text("取消") }
            },
        )
    }

    if (editUrl) {
        var edited by remember { mutableStateOf(url) }
        var editError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editUrl = false },
            title = { Text("教务网网址") },
            text = {
                Column {
                    OutlinedTextField(
                        value = edited,
                        onValueChange = {
                            edited = it
                            editError = null
                        },
                        singleLine = true,
                        label = { Text("支持任意学校的教务网地址") },
                    )
                    editError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = normalizeUrl(edited)
                    if (u == null) {
                        editError = "网址格式无效，仅支持 http/https 地址"
                    } else {
                        url = u
                        urlLoaded = true
                        registrableDomain(Uri.parse(u).host)?.let { approvedDomains = approvedDomains + it }
                        webView?.loadUrl(u)
                        editUrl = false
                        scope.launch { Graph.settingsRepository.setWebImportUrl(u) }
                    }
                }) { Text("确定并打开") }
            },
            dismissButton = {
                TextButton(onClick = {
                    edited = DEFAULT_WEB_IMPORT_URL
                    editError = null
                }) { Text("恢复默认") }
            },
        )
    }
}

private data class SslPrompt(val errCode: Int, val handler: SslErrorHandler, val host: String)

/** 只放行 http/https：无 scheme 补 https；file:// 等其他 scheme 返回 null（拒绝） */
private fun normalizeUrl(u: String): String? {
    val t = u.trim()
    if (t.isEmpty()) return DEFAULT_WEB_IMPORT_URL
    val candidate = when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        // 明确带其他 scheme（file:、ftp:、javascript: 等）一律拒绝
        Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t) -> return null
        else -> "https://$t"
    }
    val host = runCatching { java.net.URI(candidate).host }.getOrNull()
    return if (host.isNullOrBlank()) null else candidate
}

private val MULTI_PART_SUFFIXES = setOf("edu.cn", "com.cn", "gov.cn", "org.cn", "net.cn", "ac.cn", "co.uk")

/** 注册域启发式：多段后缀（edu.cn 等）取后 3 段，其余取后 2 段；站内跳转按它放行 */
private fun registrableDomain(host: String?): String? {
    if (host.isNullOrBlank()) return null
    val labels = host.lowercase().trimEnd('.').split('.')
    if (labels.size <= 2) return labels.joinToString(".")
    val last2 = labels.takeLast(2).joinToString(".")
    val last3 = labels.takeLast(3).joinToString(".")
    return if (last2 in MULTI_PART_SUFFIXES) last3 else last2
}

private fun sslErrorName(code: Int): String = when (code) {
    SslError.SSL_NOTYETVALID -> "证书尚未生效"
    SslError.SSL_EXPIRED -> "证书已过期"
    SslError.SSL_IDMISMATCH -> "证书域名不匹配"
    SslError.SSL_UNTRUSTED -> "证书不受信任"
    SslError.SSL_DATE_INVALID -> "证书日期无效"
    else -> "证书无效"
}
