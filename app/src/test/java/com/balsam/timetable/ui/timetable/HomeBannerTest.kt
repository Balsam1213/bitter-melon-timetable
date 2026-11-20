package com.balsam.timetable.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 首页横幅决策守门：优先级 提醒受限 > 备份失败 > 从未备份，最多一条（v1.7 横幅扩展） */
class HomeBannerTest {

    @Test
    fun `提醒受限优先级最高`() {
        assertEquals(
            TimetableViewModel.BannerKind.REMINDER,
            pickHomeBanner(
                reminderEnabled = true,
                reminderBlocked = true,
                lastBackupOk = false,
                hasCurrentSemesterCourses = true,
            ),
        )
    }

    @Test
    fun `用户主动关闭提醒时只抑制提醒横幅`() {
        // 提醒被用户主动关闭：不因权限不足弹提醒横幅（备份提示不受影响）
        assertNull(
            pickHomeBanner(
                reminderEnabled = false,
                reminderBlocked = true,
                lastBackupOk = true,
                hasCurrentSemesterCourses = true,
            ),
        )
        // 权限确实受限且提醒开着才弹提醒横幅
        assertEquals(
            TimetableViewModel.BannerKind.REMINDER,
            pickHomeBanner(
                reminderEnabled = true,
                reminderBlocked = true,
                lastBackupOk = true,
                hasCurrentSemesterCourses = true,
            ),
        )
    }

    @Test
    fun `备份失败优先于从未备份`() {
        assertEquals(
            TimetableViewModel.BannerKind.BACKUP,
            pickHomeBanner(
                reminderEnabled = true,
                reminderBlocked = false,
                lastBackupOk = false,
                hasCurrentSemesterCourses = true,
            ),
        )
    }

    @Test
    fun `从未备份且有课程时温和提示`() {
        assertEquals(
            TimetableViewModel.BannerKind.BACKUP_NEVER,
            pickHomeBanner(
                reminderEnabled = true,
                reminderBlocked = false,
                lastBackupOk = null,
                hasCurrentSemesterCourses = true,
            ),
        )
    }

    @Test
    fun `从未备份但无课程不提示`() {
        assertNull(
            pickHomeBanner(
                reminderEnabled = true,
                reminderBlocked = false,
                lastBackupOk = null,
                hasCurrentSemesterCourses = false,
            ),
        )
    }

    @Test
    fun `有过备份记录交给失败横幅接管`() {
        // 备份成功过（lastOk=true）
        assertNull(
            pickHomeBanner(
                reminderEnabled = true,
                reminderBlocked = false,
                lastBackupOk = true,
                hasCurrentSemesterCourses = true,
            ),
        )
        // 失败过（lastOk=false）走 BACKUP，不走 BACKUP_NEVER
        assertEquals(
            TimetableViewModel.BannerKind.BACKUP,
            pickHomeBanner(
                reminderEnabled = true,
                reminderBlocked = false,
                lastBackupOk = false,
                hasCurrentSemesterCourses = true,
            ),
        )
    }
}
