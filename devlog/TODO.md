# 待办事项

> 完成后勾掉并在 `CHANGELOG.md` 记录；新想法按优先级插入对应分区。

## v1.3（2026-10-25 已完成）

- [x] 课表显示设置：字号三档 / 紧凑模式 / 预设四主题（经典紫·靛蓝·松绿·玫粉）
- [x] 周视图小组件（第二个小组件，4×3，一天一列）
- [x] 拆分 `TimetableScreen.kt` 与 `CourseEditScreen.kt`（五种形态分文件）
- [x] `SettingsRepository` 的节假日网络同步抽到 `HolidaySyncClient`
- [x] Gradle version catalog、lint 门禁（错误清零）
- [x] 单测扩展：TextParsers / RowMapper / JsonImporter / OcrParser / 周小组件数据（单测 35 → 81）
- [x] Room 迁移测试（androidTest；3.json 从 v1.1 历史提交收割）
- [x] WorkManager 每日自动备份到 `Documents/苦瓜课表/`（滚动保留 7 份）

## v1.4（候选，未排期）

- [ ] **时间驱动：2027 年节假日预置更新**（国务院约 2026 年 11 月公布；核对 holiday-cn 2027.json → 更新 `DEFAULT_HOLIDAYS` / `DEFAULT_MAKEUP_DAYS` → 发小版本）
- [ ] detekt 接入：本机仅 JDK 25/26，detekt 1.23.8（当前最新）CLI 不支持；待其发布兼容版或本机装 JDK 21 后启用
- [ ] XlsxParser 单测（zip/XML 解析，需 xlsx 测试夹具）
- [ ] 课表页顶栏日期徽章与周次展示优化
- [ ] 备份恢复入口（从 Documents 选 JSON 直接恢复，现在要手动走导入）

## 明确不做

- `.xls` 支持（依赖重、有转存替代方案，README 已说明）
- 多语言 i18n、平板/横屏适配（受众为中文手机用户，除非出海）
- iOS 版（旧计划已移除）
- 任意取色器、自定义主题色（预设主题可选，自由配色不做）
