# PlatformTool 开发与代理协作规则

本文件适用于整个仓库。任何开发者或自动化代理修改项目之前都必须先阅读本文件；若子目录以后增加更具体的 `AGENTS.md`，子目录规则可补充但不得放宽这里的安全和兼容性要求。

## 1. 开始工作前

1. 阅读 `docs/REFACTORING_EXECUTION_PLAN.md`，确认当前阶段、任务编号、依赖和验收条件。
2. 阅读 `CHANGELOG.md` 的 `Unreleased` 和“已知问题”。
3. 执行 `git status --short` 并检查相关文件 diff。
4. 当前工作区可能包含维护者未提交的修改。现已知包括版本号以及距离传感器 Raw 数据展示；这些修改不得被覆盖、回滚或顺手重写。
5. 不得使用 `git reset --hard`、`git checkout --`、`git clean -fd` 等可能破坏用户工作的命令，除非维护者明确指定精确目标并授权。
6. 开始实现前说明准备处理的任务编号；一次变更应聚焦一个可验收的任务或紧密相关的小批次。

## 2. 架构约束

目标依赖方向为：

```text
app → feature:* → core:*
app ───────────→ core:*
```

- `core:*` 不得依赖 `feature:*`。
- feature 之间原则上不得直接依赖；共享能力下沉到合适的 core 接口。
- 在正式拆 Gradle 模块前，也必须按 `presentation/domain/data` 和目标模块边界组织包。
- UI 层不得直接打开文件、执行 Shell、访问串口、创建长生命周期线程或持有后台播放器。
- Activity/Fragment 负责渲染状态和转发事件；业务状态放入 ViewModel 或更低层。
- 新增功能必须通过 `ToolRegistry`/`ToolDescriptor` 接入首页，不得继续在 MainActivity 中增加成对的硬编码点击绑定。
- 禁止运行时下载并执行 APK、DEX 或任意插件代码。

## 3. 增量迁移规则

- 不进行一次性全量 Java 到 Kotlin、XML 到 Compose 或多模块改造。
- 新代码优先 Kotlin；修改现有 Java 时，只有在能显著降低风险且测试覆盖充分时才迁移文件语言。
- 旧实现必须保留到新实现通过对应验收；迁移期允许适配层，但要标注删除条件。
- 每个阶段结束时所有已支持 variant 都必须能构建。
- 重构不得无意改变现有用户行为。任何有意行为变化都要在 `CHANGELOG.md` 说明。
- 大范围格式化、依赖升级、架构迁移和功能修改应拆分为不同提交。

## 4. 异步、生命周期和资源管理

- 新异步代码使用结构化并发；优先 Kotlin Coroutines 和 Flow。
- 禁止新增无所有者的裸 Thread、全局 Executor 或无法取消的 Handler 循环。
- 文件、Cursor、ParcelFileDescriptor、进程流和串口句柄必须在所有退出路径关闭。
- 队列、日志、串口数据和 UI 列表必须有明确上限或背压策略。
- 磁盘、SAF、数据库、sysfs、Shell 和可能阻塞的系统调用不得运行在主线程。
- 捕获异常时不得静默吞掉影响用户结果的错误；转换为统一错误模型，并保留可诊断信息。
- 页面销毁后不得继续引用 View 或向已销毁 Activity 投递 UI 更新。
- 长期、用户可感知的工作使用符合系统限制的前台服务；可延期工作才使用 WorkManager。

## 5. 媒体和文档规则

- 新音视频实现统一使用 Media3，不再新增 `MediaPlayer` 或 `VideoView` 路径。
- 播放器所有权属于 Service/Session 层，页面只连接和渲染状态。
- 媒体和文档访问使用 SAF，并正确维护 persistable URI permission。
- URI 授权失效必须可恢复，不得导致应用启动崩溃。
- 大目录、大文本、PDF 和 CSV 必须分批、分页或流式处理。
- Office 类文档默认外部路由；未经单独设计评审不得引入自研 Office 排版实现。
- 新文档格式通过 `DocumentReader` 注册，不在路由页面堆积分支。

## 6. 工程调试和安全规则

- Root、系统签名、ADB Bridge、App Shell 是不同 capability，不得用一个布尔值混为“可用”。
- 普通版不得声明或展示不需要的受保护权限和危险工具。
- 内置操作通过结构化 `CommandRequest` 执行，参数和命令分离；任意 Shell 文本只允许存在于明确标识的 Console 功能。
- 所有命令执行必须支持超时、取消、退出码以及 stdout/stderr 处理。
- 不得假设 `su`、`stty`、特定 `/dev/tty*`、sysfs 节点或 SELinux 策略在所有设备上存在。
- 串口配置应通过可替换后端实现；`stty` 只能作为兼容后端。
- 敏感日志、命令历史、URI、设备路径和调试结果不得进入 Release 日志或云备份。
- 新增/修改 Activity、Service、Receiver、Provider 时必须审查 `exported`、permission 和 Intent 输入。
- 危险或不可逆操作必须要求明确用户动作并展示目标；不得因为“工程工具”而跳过边界检查。

## 7. 数据和兼容性

- 新结构化数据使用 Room；新设置使用 DataStore。
- Room schema 必须导出并提交；版本变化必须提供 migration 和 migration test。
- 禁止在发布构建中使用 destructive migration 规避迁移问题。
- SharedPreferences/JSON 到新存储的迁移必须幂等，成功前不得删除旧数据。
- 已发布的 tool ID、数据库主键、外部 Intent action 和导出文件格式应保持稳定。
- 改变 CSV、JSON 或文本导出格式时，要保留旧格式读取能力或在修改记录中给出迁移说明。

## 8. UI、资源和可访问性

- 所有 UI 变更必须遵守 `docs/UI_UX_SPEC.md`；视觉一致性是 Definition of Done，不是可选美化。
- 使用集中语义 token 表达颜色、排版、间距、圆角、边界和动效，业务页面不得自行复制常量。
- 新 UI 不以现有 AppCompat 蓝绿主题为基础；采用 `docs/UI_UX_SPEC.md` 定义的 Calm Expressive、Material You Dynamic Color 和全新回退主题。
- 新应用壳和迁移页面使用 Compose Material 3；旧 XML 只作为渐进迁移兼容层，并映射同一语义 token。
- 使用 edge-to-edge、Window Size Class 和 Material 3 Adaptive；不得锁定方向或按设备型号决定布局。
- 使用稳定 Material 3/Adaptive API；实验性 Expressive 组件必须隔离封装、记录风险并可替换。
- Success、Warning、Error 和日志级别保持稳定语义，不被动态品牌色替代。
- 同类页面必须复用统一骨架和组件；不得为单个功能创建独立视觉语言。
- 同一区域只允许一个 Primary 操作；整卡可点击时不得再增加重复“打开”按钮。
- 新增用户可见文本必须放入 string resources，不得在 Java/Kotlin/XML 中硬编码。
- 通用颜色、尺寸和样式应复用资源，避免页面复制。
- 新页面必须提供加载、空数据、权限不足、不支持和失败状态。
- 可点击控件应具备合理触摸区域、content description 或可理解文本。
- 不得用固定横竖屏规避状态恢复问题；确需固定方向时记录原因并验证大屏/多窗口。
- 避免嵌套同方向滚动容器；长列表使用 RecyclerView 或合适的惰性列表。
- 每个迁移页面必须检查浅色、深色、320dp 小屏、600dp+ 宽屏和至少 1.3 倍字体。
- UI 任务交付时提供与风险匹配的截图或视觉检查记录，并与已批准的参考页面比较。

## 9. 测试要求

变更应按风险提供测试：

- 纯逻辑、解析器、状态机、能力判断：JVM 单元测试。
- Room schema 和 migration：instrumentation migration test。
- Service、SAF、导航和关键 Android 行为：instrumentation test。
- Root、串口、传感器、媒体焦点、后台限制：真机测试并记录设备/API/构建 variant。
- 修复缺陷时优先先写能复现问题的测试。

默认验证命令（Windows）：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

引入 flavor 后使用明确 variant。若因设备、Root、SDK 或已有基线问题无法执行某项验证，必须在交付说明和 `CHANGELOG.md` 中写清楚，不得声称已经通过。

## 10. 文档和修改记录

每次非纯注释的代码或配置修改必须：

1. 更新 `CHANGELOG.md` 的 `Unreleased`。
2. 更新 `docs/REFACTORING_EXECUTION_PLAN.md` 中对应任务状态。
3. 若用户使用方式、构建命令、权限授予或桥接流程改变，同步更新 `README.md`。
4. 若改变视觉 token、核心组件或页面骨架，同步更新 `docs/UI_UX_SPEC.md`。
5. 若改变模块边界、持久化结构、安全模型或主要技术选型，记录 ADR 或清晰的决策说明。

任务不得仅因为“代码已写完”标记完成；必须满足执行文档中的 Definition of Done。

## 11. 提交前检查

- `git diff` 中没有用户原有改动被覆盖。
- 没有 `.gradle/`、`build/`、APK、日志、密钥或本地路径进入提交。
- 没有新的主线程 I/O、无限队列或无法取消任务。
- 权限失败、能力缺失、进程死亡和旋转路径已考虑。
- 测试和 Lint 结果与交付说明一致。
- `CHANGELOG.md`、任务状态和必要 README 已同步。

## 12. 本地 Git 提交规则

- 每个 `P?-T??` 任务满足 Definition of Done 后，必须创建至少一个本地提交。
- 提交应是可独立构建、验证和回退的原子变更；大任务可以并且应当拆成多个提交。
- 未完成或未经验证的任务不得以完成态提交。确需中间提交时使用明确的 `wip` 描述，且不得把任务标记为完成。
- 提交前必须查看完整 diff 和暂存清单，并使用精确路径暂存；不得使用 `git add -A` 把生成物或用户无关修改一并加入。
- 不得提交 `.gradle/`、`**/build/`、APK、IDE 缓存、日志、密钥或本地环境文件。
- 不得把不同任务、纯格式化、依赖升级和业务修改混入同一提交。
- 完成任务的提交必须同步更新 `CHANGELOG.md` 和执行计划任务状态；必要时可以紧随一个独立文档提交。
- 推荐提交信息格式为 `<scope>: [任务编号] <简短结果>`；不属于阶段任务的仓库维护可使用 `docs:`、`build:` 或 `chore:`。
- 默认只创建本地提交，不自动 push、merge、rebase、amend 或改写历史。上述操作需要维护者明确要求。
- 创建提交后必须报告提交哈希、标题、包含的文件和验证结果。
