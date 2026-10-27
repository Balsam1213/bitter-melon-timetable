package com.balsam.timetable.data.importer

/**
 * 把解析过程中的异常翻译成面向学生的中文提示。
 * 各解析器自己抛的业务提示（消息已是中文）原样保留；
 * 这里兜底的是库抛出的技术性异常（如坏 zip、坏 JSON），避免用户看到英文类名。
 */
fun friendlyParseError(e: Throwable): String = when {
    // ZipException 是 IOException 的子类，SerializationException 是 IllegalArgumentException
    // 的子类，必须先判子类
    e is java.util.zip.ZipException -> "文件损坏或不是有效的 .xlsx 文件，请重新导出后再试"
    e is kotlinx.serialization.SerializationException -> "不是有效的备份 JSON 文件，请确认文件来自本应用的导出"
    e is IllegalStateException || e is IllegalArgumentException ->
        e.message ?: "无法解析该内容，请检查文件格式"
    e is java.io.IOException -> "读取文件失败，请重试或换一个文件"
    else -> e.message ?: e.javaClass.simpleName
}
