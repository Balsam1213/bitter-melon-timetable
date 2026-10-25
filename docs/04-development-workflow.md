# 开发流程与工作说明

> 从环境搭建到发版的完整流程。架构细节见 [02-architecture.md](02-architecture.md)。

## 1. 环境与构建

- **环境**：Android Studio（自带 JBR 作 Gradle JDK）+ Android SDK；Windows 下路径含中文需 `gradle.properties` 里的 `android.overridePathCheck=true`（已配）
- 常用命令（项目根目录）：

```bash
./gradlew :app:assembleDebug        # 调试包（全架构），产物在 app/build/outputs/apk/debug/
./gradlew :app:assembleRelease      # 发布包（R8 + 资源压缩 + ABI 拆分，debug 签名可直接安装）
./gradlew :app:testDebugUnitTest    # 单元测试
```

- **模拟器**：AVD `timetable_test`（Pixel 6 / Android 15 / x86_64）。注意 ABI 拆分产物：模拟器装 `app-universal-debug.apk` 或 `app-x86_64-debug.apk`，**不要**装 arm64 包（`INSTALL_FAILED_NO_MATCHING_ABIS`）

## 2. 版本规范

- 版本在 `app/build.gradle.kts`：`versionName`（如 `1.1`）+ `versionCode`（每次发版 +1）
- 设置页「关于」读 `BuildConfig.VERSION_NAME` 自动显示，**禁止在 UI 硬编码版本号**
- 版本节奏：一个小版本聚焦一个主题（v1.1 = 节假日/调休）；改动范围见 `devlog/TODO.md`

## 3. 迭代流程（每个功能/每轮改动）

1. **明确需求**：先查/更新 `docs/01-requirements.md`，有歧义先问清楚再动手
2. **数据层优先**：实体/Repository/纯逻辑函数先行；涉及 `WeekLogic`/`HolidayLogic`/解析器必须**先写或更新单测**
3. **UI 层**：对照 `docs/03-design-guidelines.md`（配色、组件、文案口径）
4. **联动检查**：任何影响课表数据的改动，确认提醒重排与小组件刷新仍走 `onDataChanged` 链路
5. **验证**：`assembleDebug` + `testDebugUnitTest` 全绿；涉及 UI 的在模拟器冒烟（节假日改动的必查项：日历标记、调休周几映射、课表徽章、小组件）
6. **文档收尾**：更新 `devlog/`（当天日期文件 + TODO + CHANGELOG）与受影响的 README/docs
7. **提交**：中文提交信息，首行一句话概括，正文列要点（参照 git log 现有风格）

## 4. 提交与分支

- 单分支 `main` 直推（个人项目）；一次提交对应一个完整可编译的改动
- 提交前自查：构建绿、测试绿、没有调试用的 `Log.d`/注释掉的代码、没有不存在的 TODO 标记（待办统一进 `devlog/TODO.md`，代码里不写 TODO 注释）

## 5. 代码约定

- Kotlin，注释用中文、写「为什么」而不是「做了什么」；能一句说清的逻辑不写注释
- **文件与目录名一律英文**（kebab-case，如 `01-requirements.md`）；文档内容用中文。既有例外：`模板/`（面向用户的导入模板）
- 枚举以字符串名存库（`ReminderMode.from()` 负责兜底映射已删除的旧值）
- 星期一律 java.time 约定 **周一=1..周日=7**；日期字符串一律 ISO `yyyy-MM-dd`
- DataStore key 命名小写下划线；新增设置项在 `SettingsRepository.Keys` 集中声明
- 无依赖注入框架：新依赖通过 `Graph` 暴露；Repository 不做 UI，Screen 不碰 DataStore

## 6. 发布检查清单

- [ ] `versionCode` +1、`versionName` 更新，`devlog/CHANGELOG.md` 补条目
- [ ] `assembleRelease` 成功，产物可安装启动
- [ ] 两份 README（英文默认 + 中文）同步更新功能描述与已知限制
- [ ] Room 结构若变更：迁移在旧版本数据上验证过（装旧版 → 升级 → 校验）
- [ ] 通知/提醒路径真机或模拟器走一遍（含重启重排）
