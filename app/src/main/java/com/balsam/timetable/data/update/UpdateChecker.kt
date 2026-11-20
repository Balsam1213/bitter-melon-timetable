package com.balsam.timetable.data.update

import com.balsam.timetable.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** releases/latest 响应里的一个安装包资产（如 app-arm64-v8a-release.apk 及其直链） */
data class ReleaseAsset(val name: String, val url: String)

/** GitHub Release 的解析结果：版本 tag、更新说明 body、安装包资产列表 */
data class ReleaseInfo(val tagName: String, val body: String, val assets: List<ReleaseAsset>)

/** 弹窗所需信息：版本 tag、更新说明、按设备 ABI 选好的安装包直链 */
data class UpdateFound(val tagName: String, val body: String, val downloadUrl: String)

/**
 * GitHub Releases 检查更新（compareVersions / parseReleaseJson / downloadUrlFor / fetchLatest）。
 * 与 HolidaySyncClient 同一模式：无状态、无存储，调用方负责线程（IO）与每日限次；
 * 网络失败返回 null 由调用方静默处理，绝不影响启动。数据源必须 https 常量（有单测守门：[UpdateCheckerTest]）。
 */
object UpdateChecker {

    /** GitHub Releases 最新版 API（匿名 GET，取 tag_name / body / assets） */
    const val RELEASES_API_URL =
        "https://api.github.com/repos/Balsam1213/bitter-melon-timetable/releases/latest"

    /**
     * 语义比较版本号：去掉 v/V 前缀与 `-` 后缀，按 `.` 分段数值比较，缺段按 0（1.6 == 1.6.0）。
     * a < b 返回负数，相等返回 0，a > b 返回正数；无法解析的段按 0 处理。
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = segments(a)
        val pb = segments(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** 是否有更新：远端 tag 语义大于当前版本（本地比远端新时为 false，开发包不弹窗） */
    fun isUpdateAvailable(currentVersion: String, latestTag: String): Boolean =
        compareVersions(latestTag, currentVersion) > 0

    /** 自动检查节流：开关开启且今天（epoch-day）还没查过才查；从未查过（null）视为该查 */
    fun shouldAutoCheck(enabled: Boolean, lastCheckDay: Long?, todayEpochDay: Long): Boolean =
        enabled && lastCheckDay != todayEpochDay

    /** 宽松解析 releases/latest 响应：只取 tag_name / body / assets（name + browser_download_url）；结构不对返回 null */
    fun parseReleaseJson(json: String): ReleaseInfo? {
        val root = runCatching { Json.parseToJsonElement(json) as JsonObject }.getOrNull() ?: return null
        val tag = (root["tag_name"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val body = (root["body"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
        val assets = (root["assets"] as? JsonArray)?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            val url = (obj["browser_download_url"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: return@mapNotNull null
            ReleaseAsset(name, url)
        } ?: emptyList()
        return ReleaseInfo(tagName = tag, body = body, assets = assets)
    }

    /**
     * 按设备 ABI（Build.SUPPORTED_ABIS 顺序即优先级）选 APK 直链。
     * 精确命中 `app-<abi>-release.apk` 优先；资产里没有（如 x86 不发布）回退 universal，再没有返回 null。
     */
    fun downloadUrlFor(assets: List<ReleaseAsset>, supportedAbis: List<String>): String? {
        if (assets.isEmpty()) return null
        for (abi in supportedAbis) {
            assets.firstOrNull { it.name == "app-$abi-release.apk" }?.let { return it.url }
        }
        return assets.firstOrNull { it.name == "app-universal-release.apk" }?.url
    }

    /** 匿名 GET releases/latest 并按 ABI 选好直链；网络/解析失败或无可用安装包返回 null */
    fun fetchLatest(supportedAbis: List<String>): UpdateFound? {
        val json = runCatching { httpGet(RELEASES_API_URL) }.getOrNull() ?: return null
        val release = parseReleaseJson(json) ?: return null
        val url = downloadUrlFor(release.assets, supportedAbis) ?: return null
        return UpdateFound(release.tagName, release.body, url)
    }

    private fun httpGet(url: String): String? {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "BitterMelonTimetable/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        conn.connect()
        if (conn.responseCode != 200) return null
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** "v1.7.0-beta" → [1, 7, 0]；无法解析的段按 0 */
    private fun segments(version: String): List<Int> =
        version.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
}
