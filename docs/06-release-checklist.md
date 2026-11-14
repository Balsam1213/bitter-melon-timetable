# 发版清单与 Release Notes 模板

> 每次发布 GitHub Release 前照此执行。安装与升级说明是 Release notes 的**固定段落**，不要省略——用户经常只看 Release 页。

## 1. 发版前检查

1. **版本号**：`app/build.gradle.kts` 的 `versionCode`（+1）与 `versionName`（如 `1.5`）。
2. **CHANGELOG**：`devlog/CHANGELOG.md` 补对应版本条目。
3. **需求/架构文档**：行为有变时同步 `docs/01-requirements.md`、`docs/02-architecture.md`。
4. **质量门禁**（本机全部通过再继续）：
   - `./gradlew :app:lintDebug` —— 错误零容忍
   - `./gradlew :app:testDebugUnitTest` —— 全绿
   - 涉及 DB 迁移的版本：先装上一版 release APK → 覆盖升级 → 核对数据完好（模拟器 `timetable_test` AVD）
5. **正式签名**：确认仓库根目录存在 `keystore.properties`（含 `storeFile`/`storePassword`/`keyAlias`/`keyPassword`，不入库）；构建时日志没有出现「回退 debug 签名」的 warn。**debug 签名的包不可发版。**
6. **构建**：`./gradlew :app:assembleRelease`，产物在 `app/build/outputs/apk/release/`：
   - `app-arm64-v8a-release.apk`、`app-armeabi-v7a-release.apk`、`app-x86_64-release.apk`、`app-x86-release.apk`、`app-universal-release.apk`
   - 发 4 个：arm64-v8a / armeabi-v7a / x86_64 / universal（x86 直链在 README 下载表中未列，避免 404 误导）。
   - 上传前抽查签名：`apksigner verify --print-certs app-arm64-v8a-release.apk`。
7. **CI**：确认 main 分支最新 commit 的 GitHub Actions（lint + 单测）绿。

## 2. 发布 Release

1. 新建 Release：tag 用 `v{versionName}`（如 `v1.5`），标题 `v{versionName}`。
2. 描述按下面模板填写（中英双语），**保留「安装说明」「升级说明」两个固定段落**，按本版实际情况修改升级段（如签名变更、迁移提示）。
3. 上传 4 个 APK，文件名保持构建产物原名（README 的直链按文件名拼 `releases/latest/download/`）。
4. 发布后自检两条直链能下载（README 首推 arm64 与 universal）。

## 3. Release Notes 模板（中文）

```markdown
## 更新内容

- （逐条列新功能 / 修复，面向用户语言）

## 安装说明

要求 Android 8.0+。按设备架构选择 APK，绝大多数手机选 `app-arm64-v8a-release.apk`：

| 文件 | 适用设备 |
|---|---|
| app-arm64-v8a-release.apk | 绝大多数近年的手机（推荐） |
| app-armeabi-v7a-release.apk | 较老的 32 位手机 |
| app-x86_64-release.apk | 模拟器 / x86 设备 |
| app-universal-release.apk | 不确定架构时的通用包（体积更大） |

## 升级说明

- 从 v1.4 起均为正式签名：直接用新版 APK 覆盖安装，数据自动保留。
- v1.3 及更早版本为 debug 签名，**覆盖安装前必须先卸载**：先在旧版「设置 → 导出当前学期为 JSON」备份（或找到「文档 / 苦瓜课表」里的自动备份），卸载后装新版，再到「设置 → 从备份恢复」选文件恢复。
- 升级前建议先做一次手动备份（设置 → 立即备份）。
```

## 4. Release Notes 模板（英文）

```markdown
## What's new

- (User-facing changes, one bullet per item)

## Installation

Requires Android 8.0+. Pick the APK for your device architecture — most phones should take
`app-arm64-v8a-release.apk`:

| File | For |
|---|---|
| app-arm64-v8a-release.apk | Virtually all modern phones (recommended) |
| app-armeabi-v7a-release.apk | Older 32-bit phones |
| app-x86_64-release.apk | Emulators / x86 devices |
| app-universal-release.apk | Universal fallback if unsure (larger) |

## Upgrading

- v1.4 and later all use the formal signing key: install the new APK over the old one; your data is kept.
- v1.3 and earlier were signed with the debug key — **uninstall before covering-install**: first export
  your timetable (Settings → export JSON, or use the daily auto-backup under Documents/苦瓜课表),
  uninstall, install the new version, then restore via Settings → Restore from backup.
- A manual backup right before upgrading (Settings → Back up now) is always a good idea.
```

## 5. 发布后

1. README 下载表的直链按文件名自动指向 `releases/latest`，无需改动；确认无 404。
2. `devlog/TODO.md` 勾掉已完成项。
3. 如有影响用户迁移/数据的变化，同步更新 `docs/05-user-manual.md` 的「安装与升级」章节。
