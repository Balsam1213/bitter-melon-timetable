package com.balsam.timetable.data.holiday

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守住「节假日同步始终 HTTPS」的承诺：架构文档明确节假日同步是教务网之外
 * 唯一的网络访问点，且数据源必须是 https 常量。将来新增镜像时此测试会先红。
 */
class HolidaySyncClientTest {

    @Test
    fun `节假日同步镜像全部为https且非空`() {
        assertTrue(HolidaySyncClient.MIRROR_URL_TEMPLATE.isNotEmpty())
        assertTrue(
            "存在非 https 的镜像地址：" + HolidaySyncClient.MIRROR_URL_TEMPLATE.filterNot { it.startsWith("https://") },
            HolidaySyncClient.MIRROR_URL_TEMPLATE.all { it.startsWith("https://") },
        )
    }

    @Test
    fun `镜像模板可按年份填充`() {
        val urls = HolidaySyncClient.MIRROR_URL_TEMPLATE.map { it.replace("{year}", "2027") }
        assertTrue(urls.all { it.contains("2027") && !it.contains("{year}") })
        assertTrue(urls[0].endsWith("2027.json"))
    }
}
