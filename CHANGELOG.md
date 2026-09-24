# 修改记录

本文件记录 PlatformTool 中对用户行为、架构、构建、数据和维护流程有影响的修改。格式参考 Keep a Changelog，但内容以本项目的工程重构需要为准。

## 维护规则

1. 所有非纯注释的代码或配置修改，都应在同一提交中更新本文件。
2. 尚未发布的修改写入 `Unreleased`，发布时再移动到对应版本并填写日期。
3. 记录“发生了什么”和影响范围，不复制提交标题，也不写实现过程流水账。
4. 破坏兼容性的修改必须以 **Breaking** 标识，并写明迁移方法。
5. 数据库、DataStore、SharedPreferences、文件格式和 SAF URI 的迁移必须单独列出。
6. Root、系统权限、ADB Bridge、串口和 exported 组件的变化必须列入“安全与权限”。
7. 仅更新构建产物、IDE 缓存或本地环境文件不得写入修改记录，也不应提交。
8. 完成 `docs/REFACTORING_EXECUTION_PLAN.md` 中的任务时，在记录后附任务编号。

条目推荐格式：

```text
- [P3-T03] 使用 Media3 Session 承载后台音频播放；旧播放列表会在首次启动时迁移。
```

## Unreleased

### 新增

- 距离传感器页面增加完整 `SensorEvent.values`、精度和时间戳 Raw 数据展示。
- [P0-T04] 新增 JVM 测试骨架与 `MainActivity` 启动 instrumentation test，配置 AndroidX Test Runner，并提供统一的 `ciCheck` 验证命令。
- [DOC] 新增可执行的重构计划，包含架构边界、阶段任务、验收条件和测试矩阵。
- [DOC] 新增根目录 `AGENTS.md`，统一后续自动化代理和开发者的实施约束。
- [DOC] 建立本修改记录及维护规则。
- [DOC] 在 README 中增加重构计划、修改记录和协作规则入口。
- [DOC] 规定完成任务必须形成可验证、可回退的原子本地提交，且默认不自动推送或改写历史。
- [DOC] 新增全应用 UI/UX 视觉规范，定义主题 token、页面骨架、组件、响应式布局、无障碍和视觉评审门禁。
- [DOC] 在重构计划中增加 P1-T05 设计系统基线和 P6-T05 全应用视觉一致性验收。
- [DOC] UI 目标调整为全新的 Calm Expressive：Compose Material 3 Expressive、Material You 动态配色、edge-to-edge 和 Material 3 Adaptive；现有 AppCompat 蓝绿主题不再作为设计基础。

### 修改

- 应用版本由 `1.31`（32）更新为 `1.32`（33）。
- [P0-T01] 已确认并保护重构前的工作区改动，版本号与距离传感器 Raw 数据展示均已建立本地提交回退点。
- [P0-T02] 新增根目录 Git 忽略规则，并停止跟踪 Gradle 缓存、构建产物、IDE 本地配置和 `local.properties`；本地文件保留不变。
- [P0-T03] 建立主机基线：Debug APK 可构建，单元测试任务可执行，Lint 为 0 error、201 warning（硬编码文本 129、`SetTextI18n` 31、Overdraw 12、其他 29）；API 34 `rk3588s_u` 真机启动测试通过。
- [P1-T01] 引入 Gradle Version Catalog 和 Kotlin Android 2.0.21，启用 ViewBinding，并将 Java/Kotlin 编译目标统一为 JVM 17；单模块阶段继续使用根构建脚本承载共享约定。

### 修复

- 暂无。

### 数据迁移

- 暂无。

### 安全与权限

- 暂无。

### 已知问题

- P0-T03 的 API 23～28 真机冒烟测试尚未执行；2026-09-24 复核时仅有 API 34 `rk3588s_u` 在线，Android SDK 未安装 API 23～28 AVD/system image，任务保持阻塞且不得标记完成。
- 当前 Windows 主机的 Java NIO 在默认临时目录创建 AF_UNIX pipe 时返回 `Invalid argument`；Gradle 基线命令需为进程设置 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp`。

## 发布模板

发布时复制下面的结构，并从 `Unreleased` 移动已交付条目：

```markdown
## [版本号] - YYYY-MM-DD

### 新增

### 修改

### 修复

### 数据迁移

### 安全与权限

### 已知问题
```
