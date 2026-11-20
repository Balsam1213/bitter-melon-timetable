# 待办事项

> 完成后勾掉并在 `CHANGELOG.md` 记录；新想法按优先级插入对应分区。

## v1.4（2026-10-28 已完成）

- [x] **P0 安全与可靠性加固**：教务网导入安全收紧（明文警告/站外确认/证书按站记忆/file 禁用）、正式签名方案、导入事务化 + 完全重复跳过 + 无效校验、提醒决策抽纯函数、README 联网描述修正
- [x] **P1 可用性**：首次导入「先建个学期」确认窗 + 三步引导文案；预览页逐条问题标记（默认不勾选）；解析失败中文原因（friendlyParseError / OCR 空结果 / JSON 空条目）；备份状态三态展示；精确闹钟与通知受限红字引导
- [x] 换正式签名发版（旧版需卸载重装，Release 说明写清）

## v1.3（2026-10-25 已完成）

- [x] 课表显示设置：字号三档 / 紧凑模式 / 预设四主题（兰紫·靛蓝·松绿·玫粉）
- [x] 周视图小组件（第二个小组件，4×3，一天一列）
- [x] 拆分 `TimetableScreen.kt` 与 `CourseEditScreen.kt`（五种形态分文件）
- [x] `SettingsRepository` 的节假日网络同步抽到 `HolidaySyncClient`
- [x] Gradle version catalog、lint 门禁（错误清零）
- [x] 单测扩展：TextParsers / RowMapper / JsonImporter / OcrParser / 周小组件数据（单测 35 → 81）
- [x] Room 迁移测试（androidTest；3.json 从 v1.1 历史提交收割）
- [x] WorkManager 每日自动备份到 `Documents/苦瓜课表/`（滚动保留 7 份）

## v1.5（2026-11-15 已完成）

- [x] **从备份恢复**：扫描备份目录 + 数据预览 + JSON/版本校验 + 合并/完整两种模式 + 事务恢复失败回滚 + 完整恢复前自动快照 + 从文件选择（换机）+ 无学期时按备份建学期
- [x] **首启三步向导**：创建学期（预填可改）→ 选导入方式（复用导入预览，含冲突提示）→ 就绪页（提醒引导）；SemesterDefaults 统一默认值口径
- [x] **导入预览时间冲突提示**（`ImportPlanner.conflictsAmong`，仅提示不拦截）
- [x] **ReminderPlanner 严格 7×24h 窗口**（修 8 自然日越界）+ 边界单测（恰好 7 天/超 1 分钟/跨周末/跨月/跨年）
- [x] **WebView 安全再收紧**：混合内容默认禁止、第三方 Cookie 默认关闭（⋮ 菜单可开、持久化）、「清除教务网登录状态」、外部 scheme 白名单（tel/mailto/sms/geo，拒绝 intent:）
- [x] **WebImportBus 加固**：consume() 读后即清 + 未发布退出清理 + 架构文档使用约束
- [x] **本地优先措辞**：README 双语、设置页关于、空状态文案、需求文档
- [x] 用户手册 `docs/05-user-manual.md`、发版清单与 Release notes 模板 `docs/06-release-checklist.md`
- [x] HolidaySyncClient UA 跟随版本号 + 镜像 URL https 守门单测

## v1.5.1（2026-11-15 已完成）

- [x] **补验：font_scale 1.5 下过一遍设置页与导入弹窗**（含 v1.5 恢复页/向导；修复 6 处布局风险，规范固化进 docs/03 第 7 节；ImportPreviewUiTest 留下 1.5 倍自动化回归线）
- [x] **2027 年节假日预置更新**（国办 2026-11 发布已核实；DEFAULT_HOLIDAYS 62 天 / DEFAULT_MAKEUP_DAYS 11 天 + SettingsDefaultsTest 守门）
- [x] Compose UI 测试（androidTest 首次覆盖 UI 层：RestoreScreen/OnboardingScreen/ImportPreview，18 个 instrumented 全绿）

## v1.6（2026-11-16 已完成）

- [x] **今日 / 本周双视图**：顶部分段切换（默认今日、跨启动记忆）；今日视图 = 状态卡 + 倒计时 + 全天节次时间轴；TodayLogic 纯函数 + 单测
- [x] **「下一节课」小组件**：课程名/地点/开课时间与倒计时；闹钟定点翻面（独立 Receiver，自续链）；NextClassLogic 纯函数 + 单测
- [x] **小组件主题色跟随应用主题**（三组件统一，浅深两套）
- [x] **首页状态横幅**：提醒权限不足 / 备份失败提升到首页（可点击跳设置、可关闭）
- [x] **WebImportBus 迁移**：解析结果改经导航 SavedStateHandle 一次性传递，全局可变变量删除
- [x] **XlsxParser 单测**：XmlPullParserFactory 重构（Android 行为不变）+ ZipOutputStream 现场造最小 xlsx 的 7 个 JVM 用例
- [x] **报错细化**：坏 XML 中文映射、教务网失败提示区分两种版式
- [x] **导入预览一键平移超界周次**（shiftWeeksIntoRange 纯函数 + 单测 + androidTest 用例）
- [x] 课表页顶栏日期展示优化（今日视图顶栏显示日期与周次）

## v1.7（2026-11-21 已完成）

- [x] **检查更新（方案已定稿，直接按要点实现）**：设置 → 关于加「检查更新」按钮；数据源 `https://api.github.com/repos/Balsam1213/bitter-melon-timetable/releases/latest`（匿名 GET，取 tag_name / body / assets）。
  - **自动检查默认关 + 手动开启**（已确认）：开启后启动时每天限一次匿名检查（DataStore 节流），发现新版弹应用内对话框（下载安装包 / 查看更新说明 / 忽略此版本）；隐私承诺维持「联网由你主动触发」，README 隐私表补一句条件性说明（开启后才有每日匿名 GET，不发送任何数据）
  - 下载按设备 ABI 选 `releases/latest/download/app-<abi>-release.apk` 直链（fallback universal），不内置 APK 下载
  - 实现拆分：`data/update/UpdateChecker.kt`（compareVersions 语义比较 / parseReleaseJson / downloadUrlFor / fetchLatest，仿 HolidaySyncClient 的 HttpURLConnection 模式）+ 纯函数单测；SettingsRepository 加 UPDATE_AUTO_CHECK / UPDATE_LAST_CHECK / UPDATE_IGNORED 三 key；启动弹窗挂 MainViewModel
  - 手动检查不受「忽略此版本」影响；fetch 失败静默（不影响启动）
- [x] 首页横幅扩展：备份从未做过但有课程的温和提示等

## 后续候选（未排期）

- [ ] **扫码迁移（跨设备二维码传课表）**：v1.5 评估后暂缓——单张二维码上限约 2.9KB，一学期课表 gzip 后经常超限需分片；需新增相机权限、预览界面与条码识别依赖。优先级低，先用「JSON 文件 + 从文件选择恢复」
- [ ] detekt 接入：CI 是 JDK 21（可跑 detekt 1.23.8），但该版本官方兼容表只到 Gradle 8.x / Kotlin 2.0.21，对本仓 Gradle 9.7 / Kotlin 2.4 有解析风险；待 detekt 2.0 线（面向新栈）稳定后在 CI spike 验证后接入
- [ ] OCR 解析失败细化到逐格子（部分格子无法识别时给出格子级反馈；工作量大收益低，暂缓）

## 竞争力候选（P2，非必做）

- [ ] 课表与系统日历单向同步（导出到日历）
- [ ] 课程统计 / 考试安排 / 待办
- [ ] 可选加密备份（本地备份与换机迁移 v1.5 已就绪；**跨设备云同步非必做**，本地存储与隐私是产品定位）

## 明确不做

- `.xls` 支持（依赖重、有转存替代方案，README 已说明）
- 多语言 i18n、平板/横屏适配（受众为中文手机用户，除非出海）
- iOS 版（旧计划已移除）
- 任意取色器、自定义主题色（预设主题可选，自由配色不做）
