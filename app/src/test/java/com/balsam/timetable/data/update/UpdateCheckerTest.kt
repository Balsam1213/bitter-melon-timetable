package com.balsam.timetable.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UpdateChecker 纯函数用例 + https 守门（数据源必须是 https 常量，同 HolidaySyncClientTest 的约定）。
 * fetchLatest 的网络路径与 HolidaySyncClient.fetch 一样不做 JVM 单测，靠内部 runCatching 兜底。
 */
class UpdateCheckerTest {

    // ---------------- https 守门 ----------------

    @Test
    fun `检查更新数据源为https常量且指向本仓库`() {
        assertTrue(UpdateChecker.RELEASES_API_URL.startsWith("https://"))
        assertTrue(UpdateChecker.RELEASES_API_URL.contains("Balsam1213/bitter-melon-timetable"))
        assertTrue(UpdateChecker.RELEASES_API_URL.endsWith("/releases/latest"))
    }

    @Test
    fun `Releases 网页兜底链接为https且指向本仓库`() {
        assertTrue(UpdateChecker.RELEASES_PAGE_URL.startsWith("https://"))
        assertTrue(UpdateChecker.RELEASES_PAGE_URL.contains("Balsam1213/bitter-melon-timetable"))
        assertTrue(UpdateChecker.RELEASES_PAGE_URL.endsWith("/releases/latest"))
    }

    // ---------------- compareVersions ----------------

    @Test
    fun `compareVersions 常规大小比较`() {
        assertTrue(UpdateChecker.compareVersions("1.7.0", "1.6.2") > 0)
        assertTrue(UpdateChecker.compareVersions("1.6.2", "1.7.0") < 0)
        assertEquals(0, UpdateChecker.compareVersions("1.6.2", "1.6.2"))
    }

    @Test
    fun `compareVersions 忽略v前缀与连字符后缀`() {
        assertEquals(0, UpdateChecker.compareVersions("v1.7.0", "1.7.0"))
        assertEquals(0, UpdateChecker.compareVersions("V1.7.0", "v1.7.0"))
        assertEquals(0, UpdateChecker.compareVersions("v1.7.0-beta", "v1.7.0"))
    }

    @Test
    fun `compareVersions 段数不同与两位数段`() {
        assertEquals(0, UpdateChecker.compareVersions("1.6", "1.6.0"))
        assertTrue(UpdateChecker.compareVersions("1.10", "1.9") > 0)
        assertTrue(UpdateChecker.compareVersions("2.0", "1.9.9") > 0)
    }

    @Test
    fun `isUpdateAvailable 本地比远端新时为false`() {
        assertTrue(UpdateChecker.isUpdateAvailable("1.6.2", "v1.7.0"))
        assertTrue(!UpdateChecker.isUpdateAvailable("1.7.0", "v1.7.0"))
        assertTrue(!UpdateChecker.isUpdateAvailable("1.8.0", "v1.7.0"))
    }

    // ---------------- shouldAutoCheck 每日节流 ----------------

    @Test
    fun `自动检查节流四态`() {
        val today = 20_000L
        // 开关关闭永不检查
        assertTrue(!UpdateChecker.shouldAutoCheck(enabled = false, lastCheckDay = null, todayEpochDay = today))
        // 从未检查过 → 该查
        assertTrue(UpdateChecker.shouldAutoCheck(enabled = true, lastCheckDay = null, todayEpochDay = today))
        // 昨天查过 → 该查
        assertTrue(UpdateChecker.shouldAutoCheck(enabled = true, lastCheckDay = today - 1, todayEpochDay = today))
        // 今天查过 → 不查
        assertTrue(!UpdateChecker.shouldAutoCheck(enabled = true, lastCheckDay = today, todayEpochDay = today))
    }

    // ---------------- parseReleaseJson ----------------

    @Test
    fun `解析 GitHub releases 响应`() {
        val json = """
            {
              "url": "https://api.github.com/repos/Balsam1213/bitter-melon-timetable/releases/1",
              "tag_name": "v1.7.0",
              "name": "v1.7.0",
              "body": "## 更新内容 - 检查更新",
              "assets": [
                {
                  "name": "app-arm64-v8a-release.apk",
                  "browser_download_url": "https://github.com/Balsam1213/bitter-melon-timetable/releases/download/v1.7.0/app-arm64-v8a-release.apk"
                },
                {
                  "name": "app-universal-release.apk",
                  "browser_download_url": "https://github.com/Balsam1213/bitter-melon-timetable/releases/download/v1.7.0/app-universal-release.apk"
                }
              ]
            }
        """.trimIndent()
        val info = UpdateChecker.parseReleaseJson(json)!!
        assertEquals("v1.7.0", info.tagName)
        assertTrue(info.body.contains("检查更新"))
        assertEquals(2, info.assets.size)
        assertEquals("app-arm64-v8a-release.apk", info.assets[0].name)
    }

    @Test
    fun `解析失败或缺tag返回null`() {
        assertNull(UpdateChecker.parseReleaseJson("not json"))
        assertNull(UpdateChecker.parseReleaseJson("""{"body":"没有tag_name"}"""))
        assertNull(UpdateChecker.parseReleaseJson("""[1,2,3]"""))
    }

    @Test
    fun `缺assets与body时以空值补齐`() {
        val info = UpdateChecker.parseReleaseJson("""{"tag_name":"v1.8.0"}""")!!
        assertEquals("v1.8.0", info.tagName)
        assertTrue(info.assets.isEmpty())
        assertEquals("", info.body)
    }

    // ---------------- downloadUrlFor ----------------

    @Test
    fun `按ABI优先级精确命中`() {
        val assets = listOf(
            ReleaseAsset("app-armeabi-v7a-release.apk", "https://x/7a.apk"),
            ReleaseAsset("app-arm64-v8a-release.apk", "https://x/64.apk"),
            ReleaseAsset("app-universal-release.apk", "https://x/univ.apk"),
        )
        assertEquals("https://x/64.apk", UpdateChecker.downloadUrlFor(assets, listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("https://x/7a.apk", UpdateChecker.downloadUrlFor(assets, listOf("armeabi-v7a")))
    }

    @Test
    fun `x86未发布时回退universal`() {
        val assets = listOf(
            ReleaseAsset("app-arm64-v8a-release.apk", "https://x/64.apk"),
            ReleaseAsset("app-universal-release.apk", "https://x/univ.apk"),
        )
        assertEquals("https://x/univ.apk", UpdateChecker.downloadUrlFor(assets, listOf("x86")))
    }

    @Test
    fun `无可用安装包时返回null`() {
        val assets = listOf(ReleaseAsset("app-arm64-v8a-release.apk", "https://x/64.apk"))
        assertNull(UpdateChecker.downloadUrlFor(assets, listOf("mips")))
        assertNull(UpdateChecker.downloadUrlFor(emptyList(), listOf("arm64-v8a")))
    }
}
