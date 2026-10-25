package com.balsam.timetable.widget

import android.content.Context

/** 两个桌面小组件的统一刷新入口：数据变更/回前台时一起更新 */
object Widgets {
    suspend fun updateAll(context: Context) {
        TodayWidget().updateAll(context)
        WeekWidget().updateAll(context)
    }
}
