<!-- Release notes 固定尾段：安装与升级说明（由 release.yml 自动附加在 CHANGELOG 段落之后；改动需同步 docs/06 模板） -->

## 安装说明

要求 Android 8.0+。按设备架构选择 APK，绝大多数手机选 `app-arm64-v8a-release.apk`：

| 文件 | 适用设备 |
|---|---|
| app-arm64-v8a-release.apk | 绝大多数近年的手机（推荐） |
| app-armeabi-v7a-release.apk | 较老的 32 位手机 |
| app-x86_64-release.apk | 模拟器 / x86 设备 |
| app-universal-release.apk | 不确定架构时的通用包（体积更大） |

## 升级说明

- v1.4 起均为正式签名：直接用新版 APK 覆盖安装，数据自动保留。
- v1.3 及更早版本为 debug 签名，**覆盖安装前必须先卸载**：先在旧版「设置 → 导出当前学期为 JSON」备份（或找到「文档 / 苦瓜课表」里的自动备份），卸载后装新版，再到「设置 → 从备份恢复」选文件恢复。
- 升级前建议先做一次手动备份（设置 → 立即备份）。

---

## Installation

Requires Android 8.0+. Pick the APK for your device architecture — most phones should take `app-arm64-v8a-release.apk`:

| File | For |
|---|---|
| app-arm64-v8a-release.apk | Virtually all modern phones (recommended) |
| app-armeabi-v7a-release.apk | Older 32-bit phones |
| app-x86_64-release.apk | Emulators / x86 devices |
| app-universal-release.apk | Universal fallback if unsure (larger) |

## Upgrading

- v1.4 and later all use the formal signing key: install the new APK over the old one; your data is kept.
- v1.3 and earlier were signed with the debug key — **uninstall before covering-install**: first export your timetable (Settings → export JSON, or use the daily auto-backup under Documents/苦瓜课表), uninstall, install the new version, then restore via Settings → Restore from backup.
- A manual backup right before upgrading (Settings → Back up now) is always a good idea.
