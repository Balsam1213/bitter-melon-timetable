# 待办事项

> 完成后勾掉并在 `CHANGELOG.md` 记录；新想法按优先级插入对应分区。

## v1.2（计划中）

### 自定义周次（本版最大改动）
- [ ] Lesson 支持任意周集合（如「1-8, 11-16 周」），替代「连续区间 + 每周/单周/双周」
- [ ] Room 迁移 v3→v4：旧区间+pattern 展开为集合；顺带删除废弃的 `TimeSlot.isBreak` 列
- [ ] 课程编辑页新增 WeeksPicker（周次多选网格 + 全选/单周/双周快捷键）
- [ ] 各导入解析（xlsx/CSV/OCR/教务网）经统一转换函数入库；JSON 导出保持新旧格式兼容
- [ ] `WeekLogic.conflicts` 改为集合求交（替换逐周循环）

### 课表分享与搜索
- [ ] 周课表渲染为图片分享/保存（Canvas 自绘 + FileProvider，入口在课表页顶栏）
- [ ] 课程列表搜索（名称/教师/地点过滤）

### 打磨
- [ ] 小组件日期标签固定中文 locale（英文系统下 "10月25日 Sunday" 混排，v1.0 遗留）
- [ ] 设置页节假日「立即同步」在日历页菜单里已有，评估是否保留入口去重

## v1.3+（候选，未排期）

- [ ] 课表显示设置：字体大小 / 紧凑模式
- [ ] 周视图小组件（第二个小组件尺寸）
- [ ] 拆分 `TimetableScreen.kt`（900+ 行）与 `CourseEditScreen.kt`（四种形态同文件）
- [ ] `SettingsRepository` 的节假日网络同步抽到独立层；Room `exportSchema=true`
- [ ] Gradle version catalog、lint/detekt
- [ ] 单测扩展：TextParsers / OcrParser 文本部分 / 导入合并（超节次顺延补行）

## 明确不做

- `.xls` 支持（依赖重、有转存替代方案，README 已说明）
- 多语言 i18n、平板/横屏适配（受众为中文手机用户，除非出海）
- iOS 版（旧计划已移除）
