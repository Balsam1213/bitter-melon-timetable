package com.balsam.timetable.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.util.zip.ZipException

/** 异常 → 用户可理解文案的映射。注意子类优先：ZipException ⊂ IOException，SerializationException ⊂ IllegalArgumentException */
class ParseErrorsTest {

    @Test
    fun `坏 xlsx 的 zip 异常映射为文件损坏`() {
        assertEquals(
            "文件损坏或不是有效的 .xlsx 文件，请重新导出后再试",
            friendlyParseError(ZipException("bad zip")),
        )
    }

    @Test
    fun `序列化异常映射为 JSON 文案且优先于 IllegalArgumentException`() {
        // SerializationException 是 IllegalArgumentException 的子类，必须先命中 JSON 分支
        assertEquals(
            "不是有效的备份 JSON 文件，请确认文件来自本应用的导出",
            friendlyParseError(kotlinx.serialization.SerializationException("Unexpected JSON token")),
        )
    }

    @Test
    fun `解析器业务提示原样保留`() {
        assertEquals(
            "旧版 .xls 不支持，请在 Office / WPS 中另存为 .xlsx",
            friendlyParseError(IllegalArgumentException("旧版 .xls 不支持，请在 Office / WPS 中另存为 .xlsx")),
        )
        assertEquals(
            "没有解析出课程，请按模板格式填写（列：星期、节次、课程…）",
            friendlyParseError(IllegalStateException("没有解析出课程，请按模板格式填写（列：星期、节次、课程…）")),
        )
    }

    @Test
    fun `无消息的业务异常给通用兜底`() {
        assertEquals("无法解析该内容，请检查文件格式", friendlyParseError(IllegalArgumentException()))
    }

    @Test
    fun `普通 IO 异常映射为读取失败`() {
        assertEquals("读取文件失败，请重试或换一个文件", friendlyParseError(IOException("disk full")))
    }

    @Test
    fun `其他异常有消息用消息无消息用类名`() {
        assertEquals("some crash", friendlyParseError(RuntimeException("some crash")))
        assertEquals("RuntimeException", friendlyParseError(RuntimeException()))
    }
}
