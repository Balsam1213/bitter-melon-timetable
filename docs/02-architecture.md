# 技术架构

> 面向开发者的实现说明。需求见 [01-requirements.md](01-requirements.md)。

## 1. 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin，Java 11 兼容 |
| UI | Jetpack Compose + Material 3（预设四主题，默认兰紫，不用动态取色） |
| 架构 | 单模块 `:app`，MVVM（Screen + ViewModel + Repository），`Graph.kt` 手写 DI，无 Hilt |
| 数据 | Room（`timetable.db`，version 4）+ DataStore Preferences（`settings`） |
| 小组件 | Glance 1.2（今日课程 + 周视图） |
| 后台任务 | WorkManager（每日自动备份） |
| OCR | ML Kit 中文识别（模型内置，ABI 拆分省体积） |
| HTML 解析 | Jsoup（教务网导入） |
| 序列化 | kotlinx-serialization（JSON 备份/导出） |
| 构建 | Gradle version catalog（`gradle/libs.versions.toml`）；AGP lint 纳入门禁（错误零容忍） |
| SDK | minSdk 26 / targetSdk 36 / compileSdk 37，AGP 9.x + Kotlin 2.x + KSP |

## 2. 代码结构

```
app/src/main/java/com/balsam/timetable/
├── TimetableApplication.kt   # 入口；onCreate 挂每日节假日同步 + 自动备份排期
├── Graph.kt                  # 全局依赖容器；repository.onDataChanged 统一触发重排提醒+Widgets.updateAll
├── data/
│   ├── db/        Entities / Daos / AppDatabase（Room，Flow + suspend 双份 DAO）
│   ├── model/     Types(枚举/调色板/AppSettings/HomeViewMode) / WeekLogic(周次与冲突) / WeeksCodec(周次集合编解码与标签) / HolidayLogic(节假日差集与解析) / SemesterDefaults(学期默认值口径) / TodayLogic(今日视图三态与倒计时)
│   ├── repo/      TimetableRepository(课程/学期/节次/导入合并/导出/恢复) / SettingsRepository(设置+节假日存储)
│   ├── holiday/   HolidaySyncClient（holiday-cn 联网拉取，唯一网络职责，镜像 URL 全 https 常量）
│   ├── backup/    AutoBackup（每日备份 Worker + MediaStore 读写与滚动清理 + 恢复前快照）/ BackupValidator（备份 JSON 校验）
│   ├── html/      HtmlTimetableParser（教务网选课页 → 导入模型）+ WebImportBus（一次性交接通道，仅限 WebImportScreen↔ImportScreen）
│   └── importer/  XlsxParser / TextParsers(CSV,JSON) / OcrParser / ImportPlanner(校验/去重/冲突/入库计划) / ImportModels
├── remind/        ReminderPlanner(排课决策纯函数) / ReminderScheduler(取数+排闹钟) / AlarmReceiver / BootReceiver / Notifier
├── ui/
│   ├── MainActivity(+MainViewModel) / AppNavHost（11 条路由：home / onboarding / course / courses / slots / semesters / settings / holidays / import / webimport / restore；起始路由按「未完成引导且无学期」动态决定）
│   ├── timetable/ TimetableScreen(骨架+今日/本周分段切换) / TodayHome(今日视图：状态卡+节次时间轴) / TimetableGrid+LessonCard(网格) / GridMetrics(字号与行高度量) / SemesterSwitchDialog / ShareImageSheet
│   ├── course/    CourseEditScreen(表单) / CourseEditSheet(弹层+详情) / CourseEditFormParts / CourseListScreen(搜索)
│   ├── importflow/ webimport/ semester/ slots/ settings/ holidays/ restore/ onboarding/
│   ├── components/Common.kt（表单小组件）+ WeeksPicker
│   └── theme/     Theme.kt(四主题×浅深配色) / HolidayBadgeColors.kt（徽章语义色）
└── widget/        TodayWidget / WeekWidget / NextClassWidget(下一节课，闹钟定点翻面) / WidgetRefreshScheduler+Receiver(自续链) / WidgetColors(应用主题→Glance 色板) / Widgets.updateAll(统一刷新入口)
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

### 4.2 提醒调度（纯通知模式）
- **决策在 `ReminderPlanner`（纯函数，与 AlarmManager 解耦，单测覆盖）**：输入学期/节次/课程/节假日与 now，输出未来 **7×24 小时**（完整时间戳窗口 `now..now+7d`，边界含；不是自然日，避免把第 8 天的晚间课纳入）的提醒事件（排序、上限 50 截断都在这里）；`ReminderScheduler` 只负责取数与真正排布闹钟
- 逐日：放假日跳过（受开关控制）→ `dow = holidayCal.effectiveDayOfWeek(date)`（调休映射周几，默认自然星期）→ 按该周几匹配 lesson + `lessonActive` → `上课时间 − 提前量` 排闹钟 → `remindAt` 落在 `now..horizon` 之外的不排
- `setAlarmClock`（Doze 免疫）优先，最多 50 个；无精确权限降级
- 触发时 `AlarmReceiver` 二次拦截放假日，触发后全量重排
- 通知 id = 闹钟序号（0..49），**不要**用课程名哈希（同名课程互顶，v1.0 的坑）
- 边界单测覆盖：恰好 7 天（含）、超 7 天 1 分钟（不含）、跨周末、跨月、跨年

### 4.3 节假日/调休（`HolidayLogic` + `SettingsRepository` + `HolidaySyncClient`）
- DataStore 6 集合 + 1 映射：放假/调休各一套 `official / added / removed`，另 `makeup_weekdays`（"yyyy-MM-dd=4"）
- **生效 = (官方 ∪ 手动) − 手动删**；同日期冲突放假优先；`makeup_weekdays` 只由用户写入
- 在线同步：`HolidaySyncClient` 依次尝试 3 个 CDN 的 holiday-cn `{year}.json`（当年+次年），宽松递归解析（`isOffDay/holiday` 双字段名兼容）；Repository 负责每日限次与按年替换官方集合
- 预置：`DEFAULT_HOLIDAYS`（2026-2027 两年，放假 62 天）、`DEFAULT_MAKEUP_DAYS`（调休 11 天），与 holiday-cn 2026/2027.json 核对一致（2027 为国办 2026-11 发布安排，有单测守门 `SettingsDefaultsTest`）
- 任何节假日写操作后必须 `reschedule()` + 刷新小组件（`HolidayViewModel.onDataChanged`）

### 4.4 数据变更联动
`Graph.repository.onDataChanged = { reschedule(); Widgets.updateAll() }`；课程/学期数据变更、设置变更、节假日变更都走这条链路（今日 / 周视图 / 下一节课三个小组件一起刷），防止闹钟/小组件与库内数据脱节。「下一节课」组件另有**定点翻面闹钟**（`WidgetRefreshScheduler`，需求码 1001 独立于提醒的 0..49）：每次数据重建后排「当前课结束 / 下节开课」时刻，触发 → updateAll → 重建数据 → 自动排下一个（自续链）；重启后 BootReceiver 的 updateAll 兜底重排。

### 4.5 导入管线
所有来源（xlsx/CSV/JSON/OCR/教务网）解析成统一导入模型（`ParsedEntry`，v1.2 起周次为集合）→ `ImportScreen` 预览（逐条勾选可编辑，周次编辑用 `WeeksPicker`，**无效条目由 `ImportPlanner.validate`（公开纯函数）逐条红字标注并默认不勾选**；**条目间时间冲突由 `ImportPlanner.conflictsAmong` 琥珀色标注——同天 + 节次相交 + 周次相交，仅提示不拦截**）→ **`ImportPlanner` 统一校验（星期/节次/周次）、周次收敛、与已有课程完全重复的条目跳过** → `TimetableRepository` 在 `db.withTransaction` 事务里执行入库，返回 `ImportResult`（新增/跳过重复/无效条目），完成反馈逐项展示。**无学期时导入先弹「先建个学期」确认窗（`pendingNewSemester`，预填可改）再入库，不静默拍开学日期**。新导入方式只需实现「来源 → 导入模型」的解析器。解析异常统一经 `friendlyParseError` 翻译成中文。JSON 备份为 v2 双写格式（weeks 为主 + 旧区间字段推导），旧版本 App 可导入。

**首启向导（`ui/onboarding/`，v1.5）**：`AppNavHost` 起始路由按「`onboarding_done` 未落 **且** 无任何学期」进向导（自愈：已有学期/标记直接进 HOME）。三步——创建学期（`SemesterDefaults` 统一预填口径：名称按月份推学年学期、开学日=本周一、20 周）→ 选导入方式（复用 ImportScreen/教务网/恢复页，从导入返回时课程数 > 0 自动进就绪页）→ 就绪页（提醒开关引导）。完成或跳过落 `onboarding_done`。学期默认值原为 `ImportViewModel`/`SemesterScreen` 各一份私有实现，已收敛到 `SemesterDefaults`。

**教务网导入的网络边界**：平台层保留明文许可（http 校园站兼容是既定需求，networkSecurityConfig 无法白名单用户动态输入的域名），风险控制在 WebView 交互层——scheme 只放行 http/https、明文加载前确认（含服务端重定向兜底）、同注册域外跳转确认、证书异常按 host 会话级记忆确认；`allowFileAccess`/`allowContentAccess` 关闭；**混合内容（https 页面加载 http 子资源）默认禁止、第三方 Cookie 默认关闭**，确有需要的校园系统在教务网导入页「⋮」菜单打开（持久化到 DataStore `web_import_allow_mixed_content` / `web_import_third_party_cookies`，默认 false）；非网页 scheme 白名单只放行 `tel:`/`mailto:`/`sms:`/`geo:`，其余（尤其 `intent:`，可拉起任意组件）一律不交给系统；「清除教务网登录状态」菜单项清 Cookie + 站点存储 + 缓存。**安全取舍记录**：`usesCleartextTraffic="true"` 是应用级配置，作用域大于教务网页面本身——接受它是因为WebView 层已有上述多层确认兜底，而其他网络组件必须且只使用 https（节假日同步 `HolidaySyncClient` 是教务网之外唯一的网络调用点，3 个镜像 URL 为 https 常量并有单测守门）。`WebImportBus` 是 WebImportScreen→ImportScreen 的一次性交接通道（`consume()` 读后即清、未发布退出即清），**仅限这两个文件使用，新增导入入口一律走导航参数 / ViewModel，不得引用该全局变量**；后续计划迁移到 SavedStateHandle 或导入状态仓库（见 devlog/TODO.md）。

### 4.6 课表分享图片（`TimetableImageExporter`）
Canvas 自绘当前查看周（1080 宽浅色长图、重叠课左右分栏、文字色按底色亮度自适应，**文字上对齐**）；经 FileProvider（`cache/share/`）走系统分享，MediaStore 保存相册（API 29+）。入口在课表页 ⋮ 菜单，弹层 `skipPartiallyExpanded` 打开即全展开。**排版与配色固定**，不随字号/紧凑/主题设置变化。

### 4.7 显示设置（`GridMetrics` + `Theme.kt`，v1.3）
- 设置项存 DataStore（`text_scale` / `compact_grid` / `color_theme`），枚举字符串名 + `from()` 兜底
- `GridMetrics.of(TextScale, compact)` 集中生成网格行高与字号：字号档位影响文字（小 10/标准 12/大 14sp 基准），紧凑只压行高（×0.82）与留白，两者正交；WeekContent 的 layout `remember` 必须以 metrics 为 key
- 主题每档浅/深两套 7 角色配色（primary 系 + secondary 系，其余 Material3 默认）；`TimetableTheme(colorTheme)` 在 MainActivity 最外层应用，`MainViewModel` 暴露 settings（首帧默认紫，加载后切换）
- 课程色板 / 节假日徽章语义色 / 分享图不随主题；Glance 小组件用系统配色不随主题

### 4.8 自动备份与从备份恢复（`data/backup/`，v1.3 备份 / v1.5 恢复）
- `AutoBackupWorker`（CoroutineWorker）每日周期任务，`Application` 启动按开关 `enqueueUniquePeriodicWork(KEEP)`；设置页可关（cancel）/「立即备份」（one-time 插队）
- `backupNow` 返回 `BackupOutcome`（Success/NoSemester/Failed）：当前学期 → `exportDoc` → JSON 写 `Documents/苦瓜课表/课程表备份-yyyyMMdd-HHmmss.json`；API 29+ MediaStore（免权限），26–28 应用外部私有目录兜底；序列化与 MediaStore 写入包 runCatching，失败不再让 Worker 静默 FAILED
- 状态写入 DataStore（`last_backup_time` + `last_backup_ok`）：Success → 时间+ok；NoSemester（新装机属正常）→ 不动状态不误报；Failed → ok=false，设置页三态展示（尚未备份/成功/失败红字+引导手动导出）
- 滚动保留最近 7 份（按时间倒序删旧；**恢复前快照也计入**）
- **从备份恢复**（`ui/restore/RestoreScreen`）：`listBackupMetas` 扫描两个存储通道并逐文件 `BackupValidator.parseAndValidate`（版本 > `ExportDoc.CURRENT_VERSION` 拒绝、学期字段校验）→ 预览（时间/学期/数据量）→ 选目标学期与模式 → `TimetableRepository.restoreDoc(semesterId, doc, replaceAll)`：**单个 `db.withTransaction`**（Room 事务可重入，内部复用 `importEntries` 的校验/去重/补节次逻辑）——合并恢复 `replaceAll=false` 不动学期字段；完整恢复 `replaceAll=true` 更新学期字段 + 整体替换节次 + 清空重建课程，任何一步失败整体回滚、原有数据分毫不动
- **完整恢复前自动快照**：`AutoBackup.writeSnapshot` 把目标学期当前 `exportDoc` 写为 `恢复前快照-<学期名>-yyyyMMdd-HHmmss.json`；**快照写失败直接中止恢复**（不给用户没有退路的覆盖）。无任何学期时（新装直接恢复）先按备份建学期再恢复，无需快照

## 5. 测试与静态检查

- `app/src/test`（纯逻辑单测，无 Android 依赖）：HolidayLogicTest、WeekLogicTest、WeeksCodecTest、TextParsersTest、RowMapperTest、JsonImporterTest、OcrParserTest、WeekWidgetDataTest、ImportPlannerTest（含 `conflictsAmong` 冲突检测与 `shiftWeeksIntoRange` 平移）、ReminderPlannerTest（含 7×24h 边界：恰好 7 天/超 1 分钟/跨周末/跨月/跨年）、BackupValidatorTest（v1/v2 兼容、版本过新拒绝、字段校验）、HolidaySyncClientTest（镜像 URL https 守门）、SettingsDefaultsTest（节假日预置守门）、TodayLogicTest（今日视图三态/倒计时）、NextClassLogicTest（下一节课跨天/调休/翻面）、XlsxParserTest（ZipOutputStream 现场造最小 xlsx）等（共 179 个）
- `app/src/androidTest`：MigrationTest（MigrationTestHelper，v3→v4 迁移在真实旧库上验证；schema 存档挂 androidTest assets）、ImportRepositoryTest（in-memory Room：重复导入跳过/无效过滤/节次补行/clearExisting/**restoreDoc 合并恢复/完整恢复/失败回滚**）、**Compose UI 测试（依赖 ui-test-junit4 + `Graph.swapForTest` 换内存库）**：RestoreScreenUiTest（恢复预览/合并执行/完整恢复二次确认）、OnboardingScreenUiTest（向导预填/创建/导入方式/就绪页）、ImportPreviewUiTest（无效与冲突标注、一键平移超界周次、**font_scale 1.5 回归线**）。UI 测试 Screen 需暴露 `vm` 默认参数注入；跑法 `connectedDebugAndroidTest -PdisableSplits`
- **规则**：动 `WeekLogic` / `WeeksCodec` / `HolidayLogic` / `ReminderPlanner` / `ImportPlanner` / 解析器 / 备份校验 / 小组件数据构建必须先补/改对应单测；数据迁移必须写转换函数单测 + instrumented 迁移测试
- lint：`:app:lintDebug` 纳入提交自查（错误零容忍）；detekt 因本机仅有 JDK 25/26 而 detekt 1.23.8 CLI 不支持，暂缓（见 TODO）
- 模拟器冒烟：`timetable_test` AVD（x86_64，装 `app-universal-debug.apk`）；涉及 DB 迁移的版本要实测「装旧版 release → 覆盖升级 → 核对数据」；instrumented 测试遇 ABI 安装冲突加 `-PdisableSplits`
