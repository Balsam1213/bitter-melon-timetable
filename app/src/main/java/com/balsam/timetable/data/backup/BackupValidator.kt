package com.balsam.timetable.data.backup

import com.balsam.timetable.data.importer.ExportDoc
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 备份 JSON 的解析与恢复前校验（纯 JVM，单测覆盖）。
 * - 兼容 v1 备份：weeks 缺省时由「区间+单双周」字段推导（见 ExportEntry.toParsedEntry）
 * - 拒绝比当前版本更新的备份，避免旧版本 App 半懂不懂地写坏数据
 * - 校验学期字段健全性，给出中文错误提示
 */
object BackupValidator {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 解析并校验备份文本；不合法抛 [IllegalArgumentException]（message 为用户可读的中文提示） */
    fun parseAndValidate(text: String): ExportDoc {
        val doc = try {
            json.decodeFromString(ExportDoc.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("不是有效的课程表备份 JSON 文件", e)
        }
        require(doc.version <= ExportDoc.CURRENT_VERSION) {
            "备份版本过新（v${doc.version}），请先把应用升级到最新版再恢复"
        }
        val sem = doc.semester
        require(sem.name.isNotBlank()) { "备份里的学期名称为空" }
        require(runCatching { LocalDate.parse(sem.startDate) }.isSuccess) {
            "备份里的开学日期无效（${sem.startDate}），应为 yyyy-MM-dd"
        }
        require(sem.totalWeeks in 1..40) { "备份里的总周数无效（${sem.totalWeeks}）" }
        require(doc.entries.isNotEmpty()) { "备份里没有课程条目，无法恢复" }
        return doc
    }
}
