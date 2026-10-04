# iOS 版「苦瓜课表」开发计划（两步走）

> 起草日期：2026-10-04
> 背景项目：苦瓜课表 v1.0（Android，Kotlin + Jetpack Compose + Room），仓库 `bitter-melon-timetable`

## 0. 已确认的路线决定

结合「本机为 Windows（不购置/不租用 Mac）+ 只装自己和朋友的 iPhone」两个条件，iOS 适配采用**两步走**：

- **第一步（现在启动）：PWA 网页版** —— 100% 在 Windows 上开发，iPhone Safari「添加到主屏幕」后当独立 App 使用。
- **第二步（V2）：SwiftUI 原生版** —— 前置条件满足时启动（见第 4 节三选一）。
- 两步**共用同一套 JSON 数据格式**（安卓版 `ExportDoc v1`），实现安卓 / iOS PWA / iOS 原生三端备份互通。

> 关键事实：iOS 原生开发工具链（Xcode）只能运行在 macOS 上，编译与签名均绕不开 Mac。PWA 路线完全不依赖 Mac，是唯一能立即在 Windows 上启动并真机使用的方案。

---

## 1. 第一步：PWA 网页版

### 1.1 技术选型

| 层 | 选型 | 说明 |
|---|---|---|
| 框架 | Vite + React + TypeScript（strict） | 主流、工具链在 Windows 上顺畅 |
| 视觉 | 自写 iOS 风格 CSS | 大标题导航、圆角卡片、系统字体栈；safe-area 安全区适配；深色模式跟随系统 + 手动切换 |
| 数据层 | Dexie（IndexedDB） | 4 实体字段与安卓版 Room 同名映射：`Semester` / `TimeSlot` / `Course` / `Lesson`（location 挂在 Lesson、一门课多时间段等既有设计保持一致） |
| 离线 | vite-plugin-pwa（Workbox） | 预缓存应用外壳，首次打开后完全离线可用 |
| Excel/CSV | SheetJS | `TextDecoder('gbk')` 兼容现有导入模板（UTF-8/GBK） |
| OCR | tesseract.js 中文模型 | 约 20MB，首次下载后离线可用；准确率中等，课表截图够用 |
| 测试 | Vitest + 浏览器 GUI 自动化 | 数据层单测用 `模板/课程表示例.json` 做双端互导断言 |

### 1.2 与安卓版功能对齐

| 功能 | PWA 实现程度 |
|---|---|
| 周课表 / 切周 / 今天高亮 / 周末开关 / 非本周淡显 / 午晚休横条 | ✓ 完整 |
| 课程编辑 / 多时间段 / 冲突检测 / 10 色配色 | ✓ 完整（色值沿用同一 palette） |
| 节次时间 / 学期管理 | ✓ 完整 |
| JSON 备份导入导出 | ✓ 完整，与安卓版互导 |
| xlsx / CSV 导入 | ✓ 完整，共用现有模板 |
| 深色模式 / 主屏图标 / 全屏 / 离线 | ✓ 完整 |
| 课前提醒 | ⚠️ 导出 .ics 日历文件导入 iOS 日历，由系统日历精确提醒（支持单双周 RRULE、提前 N 分钟） |
| 桌面小组件 | ✗ PWA 无法做小组件，用主页顶部「今日课程」卡片替代 |
| 截图 OCR | ⚠️ tesseract.js 实现 |
| 教务网导入 | ⚠️ 降级为「保存网页 HTML → 导入文件」（浏览器跨域限制），后续可加书签脚本一键复制增强 |

### 1.3 里程碑（每个里程碑可真机验收）

- **M1** 项目骨架 + 数据层：Dexie 建模 + ExportDoc v1 导入导出，与安卓导出的 JSON 互导验收
- **M2** 周课表主页 + 课程编辑（含冲突检测）
- **M3** 节次时间 + 学期管理 + 设置页 + 深色模式
- **M4** xlsx/CSV 导入 + PWA 离线化 + 部署上线（首次真正可用）
- **M5** .ics 日历提醒导出 + 截图 OCR 导入
- **M6** 教务网 HTML 文件导入 + 整体打磨

### 1.4 代码位置与部署

- 代码放**同仓库 `pwa/` 子目录**，与安卓版共享 `模板/` 和文档。
- 部署：**Cloudflare Pages**（免费、国内可达性通常较好）为主，**GitHub Pages** 为备选；M4 时用手机实测二选一。
- iPhone 端使用方式：Safari 打开网址 → 分享 → 添加到主屏幕，之后全屏独立运行、离线可用。

### 1.5 数据安全说明

iOS 对「添加到主屏幕」的 PWA 存储不做 7 天清理（浏览器普通标签页才有该限制），但为稳妥，设置页保留 JSON 导出备份入口并提示定期备份。

---

## 2. 第二步：SwiftUI 原生版（V2）

### 2.1 启动前置条件（三选一，满足其一即启动）

1. 有任何一台能装 Xcode 的 Mac（开发免费）。
2. 无 Mac 云构建：GitHub Actions macOS 云端编译（公开仓库免费）+ XcodeGen 文本化工程 + XCTest 模拟器截图迭代；装机走 SideStore/AltStore 免费签名（7 天重签）或 99 美元/年开发者账号走 TestFlight。
3. 租用云 Mac（约 30 美元/月）远程开发。

### 2.2 技术映射（安卓 → iOS）

| 安卓 | iOS 对应 |
|---|---|
| Room | SwiftData（iOS 17+）或 GRDB |
| DataStore | @AppStorage + Codable JSON |
| Compose 8 个界面 | SwiftUI 逐屏移植 |
| Glance 小组件 | WidgetKit（Widget Extension） |
| ML Kit OCR | Vision 框架（系统内置中文离线识别，效果更好且零体积） |
| Jsoup | SwiftSoup（API 对应的移植版，教务网解析可低改动移植） |
| POI/xlsx | CoreXLSX |
| AlarmManager 精确闹钟 | UNUserNotificationCenter 通知触发器（无需精确闹钟权限，实现更简单） |
| 开机重排闹钟 | 不需要（触发器由系统持有） |
| WebView 教务网导入 | WKWebView + SwiftSoup 完整保留 |

### 2.3 原生版里程碑

M0 工程+图标 → M1 数据层+JSON 互通 → M2 周课表 → M3 节次/学期 → M4 通知+节假日 → M5 xlsx/Vision OCR → M6 教务网 → M7 WidgetKit → M8 TestFlight（可选）

---

## 3. 前置工作清单

### 3.1 现在（PWA 阶段）—— 几乎为零

- [x] iPhone 一台（iOS 16.4 以上），真机验收用
- [x] GitHub 账号（已有 Balsam1213）
- [ ] 部署平台账号：到 M4 再定（按手机实测可达性），不阻塞开发
- **无需 Mac、无需 Apple 开发者账号、无需任何付费**

### 3.2 V2 启动前（存档备用）

- [ ] 第 2.1 节「三选一」的开发环境
- [ ] Apple ID（免费，侧载用）；如走 TestFlight / 上架需 Apple Developer Program（99 美元/年）
- [ ] App 图标 1024×1024（可从 `design/app-icon-source.png` 导出）
- [ ] 如上架：App Store 名称查重、截图与审核材料
- [ ] Bundle ID 沿用 `com.balsam.timetable`

---

## 4. 三端共同约定（长期有效）

1. **JSON 数据格式**：`ExportDoc v1`（`{version, semester, slots, entries}`，见 `data/importer/ImportModels.kt` 与 `模板/课程表示例.json`）为三端共同备份格式，改动需三端同步。
2. **课程配色**：10 色高区分度 palette 三端保持同一色值。
3. **导入模板**：`模板/课程表导入模板.csv` 三端共用。
