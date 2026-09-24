# PlatformTool 重构实施执行文档

> 文档状态：已批准的实施基线
>
> 适用项目：PlatformTool
>
> 当前应用版本：`1.32`（`versionCode = 33`）
>
> 最低系统版本：Android 6.0 / API 23
>
> 最后更新：2026-09-24

## 1. 文档目的

本文档把 PlatformTool 的重构目标拆成可以逐项实现、验证和回退的任务。后续开发应以本文档作为执行主线，以根目录 `AGENTS.md` 作为工程约束，以 `docs/UI_UX_SPEC.md` 作为视觉验收标准，以根目录 `CHANGELOG.md` 记录实际完成的修改。

重构期间必须始终满足以下原则：

1. 每个批次结束时项目都能独立构建和运行。
2. 先补测试和抽象，再替换已有实现。
3. 不以重写全部 UI 为前提，不进行一次性 Java 到 Kotlin 或 XML 到 Compose 的整体迁移。
4. 普通能力和 Root、系统签名、ADB Bridge 等工程能力必须有明确边界。
5. 任何迁移都要保留可回退路径，旧实现只能在新实现通过验收后删除。
6. 用户现有未提交修改属于受保护内容，不得覆盖或回滚。

## 2. 当前基线

### 2.1 现有功能

- 音频文件选择、目录扫描和播放。
- 视频文件选择、目录扫描和全屏播放。
- 串口扫描、波特率配置、文本/HEX 收发和日志导出。
- 加速度计、陀螺仪、磁力计、光线、距离传感器监测。
- Logcat、Kernel Log 和 ADB Kernel Bridge 日志查看。
- App Shell/Root Console。
- FPS 测试和自定义绘制场景。
- 手电筒、振动、屏幕常亮、触摸点和指针位置控制。
- 电池状态查看、后台 CSV 记录和曲线显示。

### 2.2 已识别的基线问题

| ID | 问题 | 风险 | 目标处理阶段 |
|---|---|---|---|
| BASE-001 | 单一 `:app` 模块和单一业务包 | 功能耦合、构建和测试边界不清晰 | P2、P6 |
| BASE-002 | Activity 同时承担 UI、线程、I/O 和业务状态 | 生命周期错误、难以测试 | P2～P5 |
| BASE-003 | 媒体库使用静态列表和 SharedPreferences JSON | 进程恢复和数据一致性弱 | P3 |
| BASE-004 | `MediaPlayer`/`VideoView` 与 Activity 绑定 | 后台播放、音频焦点和状态恢复不足 | P3 |
| BASE-005 | 电池 Service 在主线程操作 CSV/sysfs | 卡顿和 ANR 风险 | P2、P5 |
| BASE-006 | Shell、Root、Bridge 和系统 API 没有统一接口 | 错误处理和权限判断重复 | P4 |
| BASE-007 | 串口依赖厂商 `stty` 且会话绑定页面 | 兼容性和后台稳定性不足 | P4 |
| BASE-008 | 高速日志使用普通列表刷新 | 高频输入下主线程和内存压力大 | P4 |
| BASE-009 | 普通能力和受保护工程权限处于同一构建形态 | 安全、分发和兼容性冲突 | P1、P6 |
| BASE-010 | 没有自动化测试和 CI 质量门禁 | 回归不可控 | P0、P1 |
| BASE-011 | 构建产物被 Git 跟踪且缺少 `.gitignore` | 仓库污染、合并冲突 | P0 |
| BASE-012 | 现有缓存 Lint 报告包含 201 个警告 | 国际化、可访问性和布局质量不足 | P1～P6 |

当前基线报告来自仓库内已有产物，没有在制定本文档时重新构建。开始 P0 时应重新生成并保存正式基线。

## 3. 目标架构

### 3.1 最终模块边界

```text
:app
  应用入口、导航、工具目录、全局初始化

:core:model
  公共模型、Result/Error、工具描述、能力状态

:core:data
  Room、DataStore、SAF、文件索引和数据迁移

:core:ui
  主题、通用控件、权限说明、加载/空/错误状态

:core:system
  App Shell、Root、ADB Bridge、系统 API、能力检测

:feature:media
  音频、视频、媒体库、Media3 播放服务

:feature:documents
  文本、PDF、EPUB、图片/漫画和外部文档路由

:feature:device
  传感器、电池、FPS、显示和硬件测试

:feature:engineering
  串口、日志、Console 和受保护快捷工具
```

允许的依赖方向：

```text
app ───────> feature:* ───────> core:*
  └───────────────────────────> core:*

core:* 不依赖 feature:*
feature:* 原则上不直接相互依赖
```

在 P2 之前不强制立即创建全部模块。先在 `:app` 内建立同名包边界，依赖稳定后再迁移为 Gradle 模块。

### 3.2 单个功能的内部结构

```text
presentation/
  Activity/Fragment/View、ViewModel、UiState、UiEvent

domain/
  UseCase、领域模型、Repository 接口

data/
  Repository 实现、系统 API、数据库、文件和进程适配器
```

基本数据流：

```text
用户事件 → ViewModel → UseCase/Repository → 数据源或系统能力
          ↑                                  ↓
          └──────────── UiState/Flow ────────┘
```

UI 不直接创建线程、打开文件、执行 Shell、访问串口或持有长生命周期播放器。

### 3.3 工具注册中心

所有首页功能通过 `ToolDescriptor` 注册，不再在首页逐个硬编码按钮：

```text
ToolDescriptor
- id：稳定且不可随显示名称变化
- title/description/icon
- category
- destination
- requiredCapabilities
- minApi
- keywords
- visibilityPolicy
```

配套接口：

- `ToolRegistry`：返回全部编译期注册工具。
- `CapabilityResolver`：计算硬件、权限、Root、系统签名、Bridge 和 API 状态。
- `ToolAvailability`：`AVAILABLE`、`NEEDS_PERMISSION`、`NEEDS_ROOT`、`NEEDS_BRIDGE`、`UNSUPPORTED`。
- `ToolNavigator`：根据目标打开页面，避免首页直接依赖具体 Activity。

禁止从网络下载并执行 APK、DEX 或脚本作为“插件”。若未来安装包体积成为问题，应单独评审 Dynamic Feature。

## 4. 技术决策

| 项目 | 决策 | 说明 |
|---|---|---|
| 开发语言 | 新代码优先 Kotlin，Java/Kotlin 可共存 | 不做一次性语言重写 |
| 异步模型 | Kotlin Coroutines + Flow/StateFlow | 替换页面私有 Executor/Handler |
| UI | 新应用壳与迁移页面使用 Compose + Material 3 Expressive；旧 XML 迁移期共存 | 采用 Calm Expressive 方向，不做一次性 UI 重写 |
| 自适应 | Window Size Class + Material 3 Adaptive + edge-to-edge | 面向手机、平板、折叠屏、多窗口和 Android 15+ 系统栏行为 |
| 配色 | Material You Dynamic Color + 全新固定回退主题 | 不继承现有 AppCompat 蓝绿主题，状态色保持稳定语义 |
| 导航 | Navigation Component | 逐页迁移，旧 Activity 可暂时保留 |
| 结构化数据 | Room | 媒体、历史、阅读进度、收藏等 |
| 设置 | DataStore | 替代新增的 SharedPreferences 用法 |
| 媒体 | AndroidX Media3 | 音视频统一播放内核和 MediaSession |
| 文件访问 | Storage Access Framework | 不依赖广泛存储权限 |
| 延迟任务 | WorkManager | 仅用于可延期、可重试工作 |
| 持续任务 | 前台服务 | 仅用于用户明确启动且可感知的持续任务 |
| 依赖管理 | Gradle Version Catalog | 统一版本和别名 |
| 注入 | 初期构造器注入；多模块稳定后评估 Hilt | 避免过早增加生成代码复杂度 |
| 发布优化 | R8 + resource shrinking + Baseline Profile | P6 启用并验证 |

引入、升级依赖时，以实施当日官方稳定版本和兼容矩阵为准，不在本文档中锁死未来版本号。

## 5. 分阶段执行计划

状态标记：`[ ]` 未开始、`[~]` 进行中、`[x]` 完成、`[!]` 阻塞。

### P0：仓库清理与可重复基线

目标：在不改变运行行为的前提下，获得干净、可验证、可回退的开发基线。

#### P0-T01 保护当前工作区

- [x] 记录 `git status --short`。
- [x] 确认 `app/build.gradle.kts`、`SensorActivity.java`、`activity_sensor.xml` 的现有修改归属。
- [x] 在进行重构前由维护者提交、暂存或明确保留这些改动。
- [x] 不得使用 `git reset --hard`、`git checkout --` 或清理未跟踪文件覆盖用户修改。

验收：现有修改有明确归属和回退点。

#### P0-T02 建立 Git 忽略规则

- [x] 新增根目录 `.gitignore`，覆盖 `.gradle/`、`**/build/`、本地 IDE 和签名机密。
- [x] 仅从 Git 索引移除已跟踪构建产物，不删除用户本地文件。
- [x] 验证源码、Wrapper 和必要配置仍受版本控制。

验收：执行一次构建后，`git status` 不因构建缓存产生大量改动。

#### P0-T03 生成正式基线 `[!]`

- [x] 执行 `./gradlew assembleDebug`。
- [x] 执行 `./gradlew testDebugUnitTest`。
- [x] 执行 `./gradlew lintDebug`。
- [x] 把失败项和 Lint 分类记录到 `CHANGELOG.md` 或独立 issue。
- [!] 在至少一台 API 23～28 和一台 API 33+ 设备执行冒烟测试（API 34 真机已通过启动测试；2026-09-24 审计时仅连接该设备，本机也没有 API 23～28 AVD/system image，旧系统设备验证待具备测试设备后回补）。

验收：能够从干净检出构建 Debug APK，已知失败均被记录。

#### P0-T04 建立测试骨架

- [x] 建立 `app/src/test` 和 `app/src/androidTest`。
- [x] 添加至少一个 JVM smoke test 和一个启动 instrumentation test。
- [x] 为 CI 预留统一命令，不依赖 Android Studio 手工操作。

退出条件：P0 全部任务完成，且没有改变现有业务行为。

### P1：构建、质量和产品形态基础

目标：统一工程配置，建立普通版和工程版边界。

#### P1-T01 Gradle 约定

- [x] 引入 `gradle/libs.versions.toml`。
- [x] 增加 Kotlin Android 插件，新代码可使用 Kotlin。
- [x] 启用 ViewBinding。
- [x] 把 Java/Kotlin 编译目标统一为同一 JVM 版本。
- [x] 建立可复用的 Android/Kotlin 编译约定；当前仅有 `:app`，约定集中在版本目录和根脚本，待 P6 多模块拆分时再提取 convention plugin。

验证：引入 flavor 前的 `clean assembleDebug testDebugUnitTest lintDebug` 与 API 34 `connectedDebugAndroidTest` 已通过；当前由 `ciCheck` 和 flavor 专用 connected test 覆盖。Kotlin/Java 均目标 JVM 17，Kotlin 编译烟测通过。

#### P1-T02 Build Type 与 Product Flavor

- [x] 设计 `standard` 和 `engineering` flavor。
- [x] `standard` 不合并 `READ_LOGS`、`WRITE_SECURE_SETTINGS` 等受保护权限。
- [x] `engineering` 才展示 Console、Root Log、受保护设置等入口。
- [x] Debug 可启用诊断工具；Release 不泄露日志、命令历史和内部路径。
- [x] 为应用 ID、名称和版本策略形成明确规则：`standard` 保持 `com.example.platformtool` 和正式版本名，`engineering` 使用 `.engineering` 应用 ID、工程版名称和 `-engineering` 版本名后缀，可并存安装。

验证：`clean ciCheck`、两个 flavor 的 Debug/Release assemble、JVM 测试与 Lint 均通过（各 0 error、202 warning），API 34 上两个 flavor 的 instrumentation 启动测试通过。合并 Manifest 已确认 standard 不含受保护权限及串口、Root Log、Console Activity，engineering 包含对应权限和入口；3000×2000 横屏截图与 UIAutomator 层级确认首页差异、standard 快捷工具不显示触摸调试设置、engineering 显示该设置。

#### P1-T03 质量门禁

- [x] CI 执行 assemble、unit test、lint。
- [x] 新增代码不允许引入新的 Lint error。
- [x] Lint warning 按“本次修改相关优先”逐步归零，禁止一次性无审查 suppress。
- [x] Debug 开启 StrictMode，检测主线程磁盘/网络访问。
- [x] 引入内存泄漏检测，仅限 Debug。

验证：GitHub Actions 使用 JDK 17 调用统一 `ciCheck`；在不 push 的约束下，本机从 clean 状态执行同一入口并同时构建两个 Release variant，全部成功。两个 flavor 各 3 个 JVM 测试、各 1 个 API 34 instrumentation 测试通过，Lint 均为 0 error、202 warning，未新增 suppress。依赖解析确认 LeakCanary 2.14 仅存在于 Debug runtime classpath，Release 无匹配依赖；API 34 冷启动日志确认 LeakCanary 就绪且 StrictMode 已报告主线程磁盘读取。

#### P1-T04 资源和国际化基础

- [x] 新增或修改的用户可见文本全部进入 string resources。
- [x] 修复触达页面的硬编码文本、autofill 和控件兼容性问题。
- [x] 主题负责窗口背景，避免页面根布局重复绘制不透明背景。

验证：首页和快捷工具页的 XML/Java 用户文案已迁入 string resources，Lint 对两个页面及主题均无剩余告警；两个 flavor 的总 warning 从 202 降至 166（HardcodedText 127→93、Overdraw 12→10），未新增 suppress。`ciCheck`、两个 Release assemble 和 API 34 上两个 flavor 的 instrumentation 启动测试通过；3000×2000 横屏截图与 UIAutomator 层级确认文案、普通版能力隐藏及页面背景无视觉回归。

#### P1-T05 视觉设计系统基线 `[~]`

- [x] 按 `docs/UI_UX_SPEC.md` 建立浅色/深色语义颜色、排版、间距、圆角、边界和动效 token。
- [x] 建立 Compose Material 3 应用壳，迁移期为 XML 页面提供同 token 的 Material 3 Bridge 主题。
- [x] Android 12+ 支持 Dynamic Color，旧系统和用户关闭动态色时使用全新 PlatformTool Calm Expressive 回退主题。
- [ ] 全应用启用 edge-to-edge，统一处理系统栏、IME、显示缺口和手势区域 insets。
- [x] 使用 Window Size Class/Material 3 Adaptive 建立手机、宽屏和多窗口布局策略。
- [x] 建立 Top App Bar、Tool Card、Status Banner、Metric Card、Empty/Error State 和工程输出区等核心组件。
- [ ] 首页、一个标准工具页和一个工程密集页完成视觉评审，作为后续迁移基准。
- [x] 为核心组件准备浅色、深色、字体放大和宽屏预览/截图。
- [x] 停止扩展旧 AppCompat 蓝绿主题；迁移完成页面只能使用 Material 3 语义角色和扩展状态 token。

阶段验证：设计系统基础层使用 Kotlin 2.0.21 对应的 Compose Compiler 插件、Compose BOM 2024.09.03、Material 3 和稳定版 Adaptive 1.0.0；这是当前 AGP 8.5.2、compileSdk 34 构建链可验证的稳定组合，避免在视觉任务中混入工具链升级。Compose 与 XML Bridge 共享浅深色 Material 语义角色、稳定状态色、4dp 间距网格、形状和 150～250ms 动效 token；核心组件提供浅色、深色、1.3 倍字体和 840dp 宽屏预览。两个 flavor 的 Debug assemble、`ciCheck` 和 API 34 启动测试通过，Lint 仍为 0 error、166 warning，触达文件无告警。全应用 edge-to-edge 与其余两类参考页面仍在本任务后续批次完成，本任务不得提前标记完成。

首页批次验证：真实启动页已迁移为 Compose Material 3 壳，使用 Adaptive 窗口信息和批准的 600/840dp 断点在单列、双列及限制最大宽度布局间切换；整卡为唯一入口，搜索无结果提供清除操作。DataStore 保存动态/固定配色选择，API 34 冷启动验证选择可持久化。真机分别检查 360dp 深色 1.3 倍字体和 3000×2000 浅色宽屏；普通版 UI 层级不含串口、Root 日志和 Console，工程版三项均存在，运行日志无崩溃。`ciCheck`、两个 Release assemble 和两个 flavor 的 API 34 instrumentation 测试通过，Lint 为 0 error、165 warning。全应用 edge-to-edge、标准工具页和工程密集页仍待完成。

退出条件：两种产品形态都能构建；普通版不包含工程专属权限和入口；三类参考页面通过 UI/UX 规范验收。

### P2：核心架构与工具目录

目标：建立后续功能可以复用的状态、能力、数据和导航基础。

#### P2-T01 公共模型和结果类型

- [ ] 定义稳定的 `AppResult`/`AppError`，至少区分权限、能力缺失、I/O、超时、取消和未知错误。
- [ ] 错误对象保留开发诊断信息，但 UI 只展示可理解、可操作的说明。
- [ ] 定义 `ToolDescriptor`、`Capability`、`ToolAvailability`。

#### P2-T02 能力解析器

- [ ] 集中检测 API、硬件、运行时权限、Root、系统签名、ADB Bridge、文件授权。
- [ ] 检测过程不得阻塞主线程。
- [ ] 能力状态支持刷新，并能解释不可用原因。
- [ ] 设备差异通过适配器处理，不在 UI 中散落厂商判断。

#### P2-T03 工具注册和首页

- [ ] 把现有九个工具注册为 descriptor。
- [ ] 首页改为列表/网格数据驱动。
- [ ] 支持分类、搜索、收藏和最近使用的数据模型；UI 可分批交付。
- [ ] 功能不可用时展示原因和解决步骤，而不是直接崩溃或静默失败。

#### P2-T04 状态与存储基础

- [ ] 引入 DataStore，新增设置不再写 SharedPreferences。
- [ ] 引入 Room 和 schema export。
- [ ] 为数据库 migration 编写测试。
- [ ] 建立统一 I/O dispatcher/provider，测试中可替换。

#### P2-T05 电池主线程 I/O 先行修复

- [ ] 将 CSV 打开、追加、flush、关闭移到串行 I/O 上下文。
- [ ] Service 主线程只接收事件和更新轻量状态。
- [ ] 写入失败必须关闭会话、更新通知并保留可诊断原因。
- [ ] 添加慢存储和授权失效测试。

退出条件：首页不再手工绑定每个工具；核心状态和 I/O 可以被单元测试替换。

### P3：媒体域迁移

目标：建立稳定、后台友好、可恢复的统一音视频能力。

#### P3-T01 媒体数据库

- [ ] 定义 `MediaItemEntity`、播放历史和最后进度表。
- [ ] 从现有 SharedPreferences JSON 执行一次性迁移。
- [ ] URI 去重使用规范化后的稳定键。
- [ ] 启动时验证 persistable URI permission，失效条目标记为不可访问而不是直接删除。

#### P3-T02 媒体索引

- [ ] 把目录扫描从 Activity 提取为 repository/use case。
- [ ] 支持取消、进度、最大条目限制和部分失败。
- [ ] 大目录结果分批写入数据库，不一次性持有全部对象。
- [ ] MIME、扩展名和实际可播放性分别处理。

#### P3-T03 Media3 播放服务

- [ ] 建立单一 Player 所有者和 `MediaSessionService`。
- [ ] 支持音频焦点、耳机断开、通知和锁屏控制。
- [ ] 播放队列、循环模式和进度在进程恢复后可还原。
- [ ] Activity/Fragment 只连接 Session，不直接持有 Player 生命周期。

#### P3-T04 音频页面迁移

- [ ] 迁移文件选择、目录扫描、列表、播放控制。
- [ ] 验证切后台、旋转、锁屏、来电/其他音频抢占。
- [ ] 新页面验收后移除旧 `MediaPlayer` 路径。

#### P3-T05 视频页面迁移

- [ ] 使用 Media3 PlayerView。
- [ ] 支持方向变化、全屏、进度恢复和播放错误说明。
- [ ] 为字幕、倍速、多音轨预留 UI 状态和能力接口。
- [ ] 新页面验收后移除 `VideoView` 路径。

退出条件：静态 `MediaRepository` 被删除；音频后台播放和音视频状态恢复通过测试。

### P4：工程调试域迁移

目标：统一受保护能力，并让高频 I/O 有界、可取消、可诊断。

#### P4-T01 CommandRunner

- [ ] 定义 `CommandRequest`、`CommandEvent`、`CommandResult`。
- [ ] 提供 App Shell、Root Shell、ADB Bridge、System API 实现。
- [ ] 统一超时、取消、stdout/stderr、退出码和进程关闭。
- [ ] 内置命令使用参数数组或结构化请求；避免拼接不可信字符串。
- [ ] Console 的任意命令模式必须和内部命令 API 分开。

#### P4-T02 Console

- [ ] ViewModel 持有命令会话状态。
- [ ] 输出采用有界缓冲并批量刷新 UI。
- [ ] 显示当前身份和执行后端。
- [ ] 命令历史支持禁用、清除，并排除备份。
- [ ] Root 模式切换和危险操作提供明确提示。

#### P4-T03 日志

- [ ] 定义 `LogSource`：Logcat、Kernel、Bridge、File。
- [ ] 每个来源实现统一启动、停止、重连和能力说明。
- [ ] 使用环形缓冲，内存上限可配置并有测试。
- [ ] 过滤和导出在后台执行。
- [ ] 高频输入时通过批处理/节流更新 RecyclerView。

#### P4-T04 串口

- [ ] 定义 `SerialConfig`、`SerialSession`、`SerialEvent`。
- [ ] UI 与文件描述符生命周期解耦。
- [ ] 评估并实现 NDK `termios` 或经过验证的串口库，保留 `stty` 作为显式兼容后端而非唯一实现。
- [ ] 读写使用独立串行上下文，数据流有背压和上限。
- [ ] 支持设备断开、权限失败、取消和可选自动重连。
- [ ] 用户显式选择是否后台保持；需要持续连接时使用前台服务。

#### P4-T05 快捷工具

- [ ] 手电筒、振动、常亮、系统设置修改分别抽象 capability。
- [ ] 对每个操作记录执行后端和失败原因。
- [ ] App Shell、Root、Bridge 和系统签名实现可替换。

退出条件：页面中不再直接散落 Root 探测和 ProcessBuilder；串口与日志高负载测试通过。

### P5：设备与文档域

目标：完成设备工具分层，并建立可扩展的文档阅读框架。

#### P5-T01 传感器

- [ ] 建立 sensor repository 和订阅生命周期。
- [ ] 高频数据在后台采样/降频，UI 只接收适当刷新率。
- [ ] 原始值与推导值分开建模。
- [ ] 缺少传感器、异常 values 长度和注册失败均有测试。

#### P5-T02 电池与图表

- [ ] 采集、持久化、服务通知、CSV 和图表解析分离。
- [ ] 大 CSV 使用流式解析，不一次性加载全部内容。
- [ ] 图表数据抽样，避免长时间记录导致绘制量无限增长。
- [ ] 开机恢复行为符合用户显式选择和系统后台限制。

#### P5-T03 FPS 和显示工具

- [ ] 测量逻辑与绘制场景分离。
- [ ] 明确“应用帧回调 FPS”和“物理屏幕刷新率”的区别。
- [ ] 暂停和页面不可见时停止帧回调。

#### P5-T04 文档路由

- [ ] 定义 `DocumentReader` 和 reader registry。
- [ ] 基于 MIME、扩展名和能力选择阅读器。
- [ ] 统一 URI 授权、最近打开、收藏和阅读进度。
- [ ] 不支持的格式给出外部打开选项。

#### P5-T05 内置阅读器

- [ ] 文本：编码检测、搜索、大文件分块。
- [ ] PDF：分页渲染、缩放、缓存和失败恢复。
- [ ] EPUB：采用成熟引擎，保存章节与进度。
- [ ] 图片/漫画：分页、缩放和有限预加载。
- [ ] Office 类格式第一阶段只做外部路由，不自行实现排版引擎。

退出条件：文档能力能通过 reader registry 增加新格式，设备工具均不在 UI 直接执行长任务。

### P6：模块化、发布和性能验收

目标：按稳定边界拆分模块，并完成可发布质量验证。

#### P6-T01 Gradle 模块拆分

- [ ] 先拆 core，再拆 feature。
- [ ] 每次只迁移一个依赖闭环。
- [ ] 禁止 feature 之间形成循环依赖。
- [ ] Android resource 命名增加模块前缀，避免冲突。

#### P6-T02 发布优化

- [ ] Release 开启 R8 和资源压缩。
- [ ] 根据实际反射/序列化用法编写最小 keep rules。
- [ ] 生成并验证 Baseline Profile。
- [ ] 确认 Release 不包含 Debug 日志、诊断依赖和测试入口。

#### P6-T03 安全和备份

- [ ] `standard` 与 `engineering` 分别审计 Manifest。
- [ ] 关闭工程版备份，或通过 data extraction rules 排除敏感数据。
- [ ] 检查所有 exported 组件和 PendingIntent flag。
- [ ] 检查日志、命令历史、URI、串口数据和导出文件是否包含敏感内容。

#### P6-T04 性能与稳定性

- [ ] 测量冷启动、内存峰值、卡顿和电量消耗，记录测试设备和条件。
- [ ] 音频后台播放 2 小时稳定性测试。
- [ ] 视频连续切换和方向变化测试。
- [ ] 日志高吞吐 30 分钟测试。
- [ ] 串口持续收发及反复断开重连测试。
- [ ] 电池记录 8 小时以上测试。
- [ ] 大目录、大 PDF、大文本和大 CSV 测试。

#### P6-T05 全应用视觉一致性验收

- [ ] 按 `docs/UI_UX_SPEC.md` 对全部页面执行 UI 验收清单。
- [ ] 保留全部页面的浅色/深色基准截图；响应式页面增加手机/宽屏截图。
- [ ] 串行浏览所有工具，检查独立风格页面、重复入口、冲突文案和错误状态。
- [ ] 验证 320dp 小屏、600dp+ 宽屏、1.3 倍字体和系统减少动画设置。
- [ ] 验证 TalkBack/键盘焦点、点击区域、颜色对比以及非颜色状态表达。
- [ ] 视觉差异必须修复或形成有依据的例外记录，禁止以“功能可用”为由跳过。

退出条件：所有发布门禁和视觉一致性验收通过，旧架构兼容代码已删除，文档与实际结构一致。

## 6. 测试策略

### 6.1 测试金字塔

- JVM 单元测试：解析器、状态机、repository、use case、能力决策。
- Robolectric（仅确有价值时）：依赖 Android framework 的轻量行为。
- Instrumentation：Room migration、SAF、Service、导航和关键 UI。
- 真机测试：媒体焦点、后台限制、Root、串口、传感器和系统权限。
- 长稳测试：日志、串口、电池记录和媒体播放。

### 6.2 最低设备矩阵

| 设备组 | 目的 |
|---|---|
| API 23～28 普通设备 | 最低版本、旧存储和旧后台行为 |
| API 29～32 普通设备 | 分区存储和中间版本兼容 |
| API 33+ 普通设备 | 通知权限和较新后台限制 |
| Root 工程设备 | Root Shell、Kernel Log、系统设置 |
| 厂商系统签名设备 | 受保护权限、UART、SELinux 策略 |
| 无对应硬件设备 | 验证能力缺失时的降级 UI |

### 6.3 每次提交的最低验证

Windows：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

其他系统：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

若引入 flavor，应改为明确 variant，例如 `assembleStandardDebug`。命令变化后必须同步更新本文件、`README.md` 和 CI。

## 7. 迁移和数据兼容规则

1. SharedPreferences 到 DataStore/Room 的迁移必须幂等。
2. 迁移成功前不删除旧数据；迁移完成后写入版本标记。
3. Room schema 必须导出并纳入版本控制。
4. 每次数据库版本变更必须有 migration test，禁止发布构建使用 destructive migration。
5. SAF URI 授权失效不得造成启动崩溃；条目应标记为需要重新授权。
6. CSV 和导出文本格式若改变，要说明兼容性并尽量保留读取旧格式的能力。
7. `ToolDescriptor.id`、数据库主键和外部 action 名称一旦发布不得随意更改。

## 8. 提交和修改记录规范

### 8.1 本地提交规则

1. 每个 `P?-T??` 任务达到 Definition of Done 后，必须形成至少一个本地 Git 提交。
2. 一个提交必须是可独立构建、可验证、可回退的原子变更；不得为了满足“一任务一提交”而把无关修改混在一起。
3. 较大任务应拆成多个原子提交，例如“补充回归测试”“引入新实现”“迁移数据”“删除旧实现”。最后一个提交完成后才能把任务标记为 `[x]`。
4. 未完成或尚未验证的工作不得以完成态提交；确需保存中间状态时，提交说明必须明确标为 `wip`，且不得据此更新任务为完成。
5. 创建提交前必须检查 `git diff`、暂存文件清单和验证结果，使用精确路径暂存，禁止把构建产物或用户的无关修改带入提交。
6. 本地提交完成后不自动 push、merge、rebase 或改写历史；这些操作必须由维护者明确要求。
7. 除非维护者明确要求，否则不得使用 `git commit --amend` 修改已经存在的提交。
8. 每个完成任务的提交必须同步包含 `CHANGELOG.md` 和本执行文档中的状态更新；如果代码与文档需要分开提交，文档提交必须紧随其后且保持本地历史连续。

推荐提交信息格式：

```text
<scope>: [任务编号] <简短结果>
```

例如：

```text
media: [P3-T03] add Media3 playback service
engineering: [P4-T04] isolate serial session lifecycle
docs: define refactoring execution workflow
```

### 8.2 提交范围

建议提交范围：

```text
build: 构建、依赖、CI
core: 核心模型和基础设施
media: 音视频
docs: 文档阅读
device: 传感器、电池、FPS
engineering: 串口、日志、Console、快捷工具
ui: 主题和通用 UI
test: 测试和测试工具
chore: 仓库维护
```

每个功能提交必须：

1. 在 `CHANGELOG.md` 的 `Unreleased` 下增加面向维护者的记录。
2. 在本文件更新相应任务状态。
3. 说明测试命令与结果。
4. 若改变模块边界、持久化结构、安全策略或技术选型，新增 ADR 或在 PR/提交说明中记录决策依据。

不要把格式化整个项目、依赖升级和业务迁移混在同一个提交中。

## 9. 单任务 Definition of Done

一个任务只有同时满足以下条件才可标记 `[x]`：

- 行为满足任务描述和验收条件。
- 正常、错误、取消和生命周期路径均已处理。
- 新逻辑有与风险匹配的自动化测试。
- 相关构建、测试和 Lint 已执行并记录结果。
- 没有引入不受控线程、进程、文件句柄或无限内存队列。
- 新增用户可见文本使用资源文件。
- 页面通过 `docs/UI_UX_SPEC.md` 中与本次变更相关的视觉、响应式和无障碍检查。
- 权限和受保护能力有降级路径。
- `CHANGELOG.md`、本执行文档和必要的 README 已同步。
- 不包含无关改动或构建产物。

## 10. 明确不做的事项

- 不进行一次性全量 Kotlin 重写。
- 不把全面 Compose 化作为架构重构前置条件。
- 不自行实现 Office 文档排版引擎。
- 不通过申请全盘存储权限替代 SAF。
- 不把 Root 当成所有设备都存在的默认能力。
- 不允许页面直接长期持有后台任务来模拟 Service。
- 不引入运行时下载并执行代码的插件系统。
- 不为消除 Lint 数字而大范围 suppress 警告。
