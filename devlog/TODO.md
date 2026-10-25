# 待办事项

> 完成后勾掉并在 `CHANGELOG.md` 记录；新想法按优先级插入对应分区。

## v1.2（2026-10-25 已完成）

- [x] 自定义周次：Lesson 支持任意周集合（如「1-8, 11-16 周」），WeeksPicker 多选编辑，Room v3→v4 迁移
- [x] 课表分享为图片（Canvas 自绘 + FileProvider 分享 + 保存相册）
- [x] 课程搜索（名称/教师/地点）
- [x] 小组件日期固定中文 locale
- [x] 顺带清理：删除无消费方的 PatternChips/NumberField 组件

## v1.3（候选，未排期）

- [ ] 课表显示设置：字体大小 / 紧凑模式
- [ ] 周视图小组件（第二个小组件尺寸）
- [ ] 拆分 `TimetableScreen.kt`（约 1000 行）与 `CourseEditScreen.kt`（四种形态同文件）
- [ ] `SettingsRepository` 的节假日网络同步抽到独立层
- [ ] Gradle version catalog、lint/detekt
- [ ] 单测扩展：TextParsers / OcrParser 文本部分 / Room 迁移测试（MigrationTestHelper + schemas/）
- [ ] WorkManager 定时自动备份到本地文件

## 明确不做

- `.xls` 支持（依赖重、有转存替代方案，README 已说明）
- 多语言 i18n、平板/横屏适配（受众为中文手机用户，除非出海）
- iOS 版（旧计划已移除）
