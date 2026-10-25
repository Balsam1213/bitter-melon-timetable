# 技术架构

> 面向开发者的实现说明。需求见 [01-requirements.md](01-requirements.md)。

## 1. 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin，Java 11 兼容 |
| UI | Jetpack Compose + Material 3（固定品牌紫，不用动态取色） |
| 架构 | 单模块 `:app`，MVVM（Screen + ViewModel + Repository），`Graph.kt` 手写 DI，无 Hilt |
| 数据 | Room（`timetable.db`，version 4）+ DataStore Preferences（`settings`） |
| 小组件 | Glance 1.2 |
| OCR | ML Kit 中文识别（模型内置，ABI 拆分省体积） |
| HTML 解析 | Jsoup（教务网导入） |
| 序列化 | kotlinx-serialization（JSON 备份） |
| SDK | minSdk 26 / targetSdk 36 / compileSdk 37，AGP 9.x + Kotlin 2.x + KSP |

## 2. 代码结构

```
app/src/main/java/com/balsam/timetable/
├── TimetableApplication.kt   # 入口；onCreate 挂每日节假日同步
├── Graph.kt                  # 全局依赖容器；repository.onDataChanged 统一触发重排提醒+刷新小组件
├── data/
│   ├── db/        Entities / Daos / AppDatabase（Room，Flow + suspend 双份 DAO）
│   ├── model/     Types(枚举/调色板/AppSettings) / WeekLogic(周次与冲突) / WeeksCodec(周次集合编解码与标签) / HolidayLogic(节假日差集与解析)
│   ├── repo/      TimetableRepository(课程/学期/节次/导入合并/导出) / SettingsRepository(设置+节假日+同步)
│   ├── html/      HtmlTimetableParser（教务网选课页 → 导入模型）
│   └── importer/  XlsxParser / TextParsers(CSV,JSON) / OcrParser / ImportModels
├── remind/        ReminderScheduler / AlarmReceiver / BootReceiver / Notifier
├── ui/
│   ├── MainActivity / AppNavHost（9 条路由：home / course / courses / slots / semesters / settings / holidays / import / webimport）
│   ├── timetable/ TimetableScreen + TimetableViewModel（主页周网格）
│   ├── course/    CourseEditScreen(编辑+底部弹层详情) / CourseListScreen
│   ├── importflow/ webimport/ semester/ slots/ settings/ holidays/
│   ├── components/Common.kt（表单小组件）
│   └── theme/     Theme.kt（品牌色） / HolidayBadgeColors.kt（徽章语义色）
└── widget/        TodayWidget.kt（Glance 今日课程 + Receiver）
```

## 3. 数据模型（Room v4）

- `semesters`：`startDate`（第 1 周周一 "yyyy-MM-dd"）+ `totalWeeks` → 周次按日期计算，不存储
- `time_slots`：每学期节次起止时间（午休/晚休由上下午空档自动推导，不落库）
- `courses`：学期、名称、教师、`colorArgb`、备注、`reminderMode`（GLOBAL/OFF，字符串存枚举名）
- `lessons`：课程 id、`dayOfWeek` 1-7（**周一=1..周日=7，java.time 约定**）、节次区间、**`weeks` 周次集合单列**（升序逗号分隔 "1,2,...,8,11"，编解码见 `WeeksCodec`；v1.1 及以前是「区间+每周/单周/双周」三列，v4 迁移展开）、地点（地点在 Lesson 级）
- 无 ForeignKey，级联删除在 Repository 手工实现；schema 历史存档在 `app/schemas/`
- **导入侧规则**：超出已有节次数的课程自动按 45 分钟顺延补行；周次超出学期总周数的部分在入库时收敛（`TimetableRepository`）

## 4. 核心机制

### 4.1 周次计算（`WeekLogic` + `WeeksCodec`）
- `weekOf(date, startDate, totalWeeks)`：开学前/超总周返回 null
- `WeeksCodec`：周次集合 ↔ 字符串编解码、`fromRange/toRange` 与旧「区间+单双周」互转、游程分段展示标签（全学期返回 null 不显示）
- `lessonActive(week, weekSet)`：集合包含即上课
- `conflicts`：同星期 + 节次相交 + 周集合交集非空（不再逐周循环）

### 4.2 提醒调度（`ReminderScheduler`，纯通知模式）
- 未来 7 天逐日：放假日跳过（受开关控制）→ `dow = holidayCal.effectiveDayOfWeek(date)`（调休映射周几，默认自然星期）→ 按该周几匹配 lesson + `lessonActive` → `上课时间 − 提前量` 排闹钟
- `setAlarmClock`（Doze 免疫）优先，最多 50 个；无精确权限降级
- 触发时 `AlarmReceiver` 二次拦截放假日，触发后全量重排
- 通知 id = 闹钟序号（0..49），**不要**用课程名哈希（同名课程互顶，v1.0 的坑）

### 4.3 节假日/调休（`HolidayLogic` + `SettingsRepository`）
- DataStore 6 集合 + 1 映射：放假/调休各一套 `official / added / removed`，另 `makeup_weekdays`（"yyyy-MM-dd=4"）
- **生效 = (官方 ∪ 手动) − 手动删**；同日期冲突放假优先；`makeup_weekdays` 只由用户写入
- 在线同步：3 个 CDN 依次尝试 holiday-cn `{year}.json`（当年+次年），宽松递归解析（`isOffDay/holiday` 双字段名兼容），按年替换两个官方集合，每日限一次
- 预置：`DEFAULT_HOLIDAYS`（2026 放假 34 天）、`DEFAULT_MAKEUP_DAYS`（2026 调休 6 天），与 holiday-cn 2026.json 核对一致
- 任何节假日写操作后必须 `reschedule()` + 刷新小组件（`HolidayViewModel.onDataChanged`）

### 4.4 数据变更联动
`Graph.repository.onDataChanged = { reschedule(); widget.updateAll() }`；课程/学期数据变更、设置变更、节假日变更都走这条链路，防止闹钟/小组件与库内数据脱节。

### 4.5 导入管线
所有来源（xlsx/CSV/JSON/OCR/教务网）解析成统一导入模型（`ParsedEntry`，v1.2 起周次为集合）→ `ImportScreen` 预览（逐条勾选可编辑，周次编辑用 `WeeksPicker`）→ `TimetableRepository` 合并入库。新导入方式只需实现「来源 → 导入模型」的解析器。JSON 备份为 v2 双写格式（weeks 为主 + 旧区间字段推导），旧版本 App 可导入。

### 4.6 课表分享图片（`TimetableImageExporter`）
Canvas 自绘当前查看周（1080 宽浅色长图、重叠课左右分栏、文字色按底色亮度自适应，**文字上对齐**）；经 FileProvider（`cache/share/`）走系统分享，MediaStore 保存相册（API 29+）。入口在课表页 ⋮ 菜单，弹层 `skipPartiallyExpanded` 打开即全展开。

## 5. 测试

- `app/src/test`：HolidayLogicTest、WeekLogicTest、WeeksCodecTest（35 个，纯逻辑单测，无 Android 依赖）
- **规则**：动 `WeekLogic` / `WeeksCodec` / `HolidayLogic` / 解析器必须先补/改对应单测；数据迁移必须写转换函数单测
- 模拟器冒烟：`timetable_test` AVD（x86_64，装 `app-universal-debug.apk`）；涉及 DB 迁移的版本要实测「装旧版 release → 覆盖升级 → 核对数据」
