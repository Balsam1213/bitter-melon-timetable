package com.balsam.timetable.widget

import android.content.Context

/** 三个桌面小组件的统一刷新入口：数据变更/回前台/闹钟翻面时一起更新 */
object Widgets {
    suspend fun updateAll(context: Context) {
        TodayWidget().updateAll(context)
        WeekWidget().updateAll(context)
        NextClassWidget().updateAll(context)
    }
}
