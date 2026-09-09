# AGENTS.md

Mosika 协作与设计指南。修改 DSL 解析、节点语义、UI 树或结果契约前先读本文件；完整设计见 `docs/`（规则模型核心设计、规则编排交互设计、运维与开发）。

## 第一条

- 用户指令明确时直接执行，不要求用户确认或重复指令。
- 不自作主张，不擅自增加用户未明确要求的内容。

## 项目概览

Mosika 是 JDK 21、Maven 多模块的可视化规则流语言与执行内核。UI AST（其 JSON）是编辑、持久化、编译、执行、追踪与回放的唯一事实来源，模型从不从执行树反向重建。

`mosika-core` 全部是**进程内代码**，不是对外 SDK，没有跨进程/跨系统调用；所有调用方都在同一仓库里，参数来源可追溯。

## 构建与测试

```bash
mvn clean test                 # 重新生成 ANTLR 源 + 跑全部测试
mvn -pl mosika-core test       # 仅内核
mvn -pl mosika-ui -am test     # UI 树及其内核依赖
mvn -pl mosika-web -am test    # Web/API 及其依赖
mvn clean package              # 测试 + 构建各模块 JAR
mvn -pl mosika-core test -Dtest=SomeTest              # 单个测试类
mvn -pl mosika-core test -Dtest=SomeTest#someMethod   # 单个测试方法
```

测试为 JUnit 5，命名 `*Test`。不要编辑 `target/generated-sources/` 下的生成代码（`mosika-core/src/main/antlr4/Rule.g4` 的 ANTLR 产物）。改动解析、执行、并发上下文或树转换，需补对应回归测试。

## 运行参考 Web 服务

```bash
./scripts/mosika.sh start      # 构建并启动 Spring Boot 胖包
./scripts/mosika.sh dev        # 开发模式
./scripts/mosika.sh status | logs | restart | stop
```

服务于 `http://127.0.0.1:8080/`（业务场景）、`/rules`（原子规则）、`/udfs`（UDF 注册中心）、`/flow/{id}`（画布）。运行态、日志和 SQLite 库不得写进仓库；脚本把它们放在由 `MOSIKA_RUN_DIR` / `MOSIKA_DB_PATH` / `MOSIKA_PORT` 配置的运行目录下。`restart` 按端口与命令行定位 mosika 实例（含手动启动的游离实例）后重启，不依赖状态文件。

## 模块边界（严格，不得违反）

| 模块 | 负责 | 不得包含 |
| --- | --- | --- |
| `mosika-core` | DSL、规则树、执行上下文、求值、UDF 抽象与引擎、命名原子/复合规则 | 页面、持久化、Flow 产品模型、业务场景映射 |
| `mosika-udf` | 内置 Java UDF 实现（`udf/builtin/*`） | DSL/引擎内核、页面、Spring 装配 |
| `mosika-spring-boot-starter` | `@Udf` 自动扫描与 Spring 装配（`SpringUdfLoader`/`UdfBeanDefinitionRegistrar`/`MosikaUdfAutoConfiguration`） | 规则语义、执行内核、业务持久化 |
| `mosika-ui` | 可序列化 UI AST + 单向编译为 `RuleNode` | 页面布局、Web 依赖、从执行树反向恢复 UI 树 |
| `mosika-web` | 可选参考控制面：REST、SQLite、规则管理 + 可视化编辑器 | 规则语义、执行内核、以库形式替换 Core/UI |

内核只认识 `RuleDefinition` 和 `UdfDefinition`，不定义 `RuleFlow`/`RuleFlowDefinition` 或任何 Flow 专用 API。产品层（如 web）把编排单向编译成 `ruleType=2` 的 `RuleDefinition` 交给 Core，绝不把 Flow 类型、配置或 API 反向塞回 `mosika-core`。

## 内核执行模型（`mosika-core`）

- `RuleDefinition.ruleType=0` 是 JavaScript 原子规则，`ruleType=2` 是规则 ID DSL 复合规则；两者都按 `ruleId` 编译成 `RuleNode`，复合规则的运行时根是 `CompositeNode`。
- `RuleEngine` 是规则注册的唯一事实来源：注册内置规则 + 全部 `RuleDefinition`，并预编译 `ruleType=0` 的 JavaScript。`NodeGenerator.create(compositeRules)` 只接收完整复合规则表，递归展开 `CompositeNode`、复用编译结果、检测复合规则环，不维护第二份已注册规则集合。复合规则表里没有的 `ruleId` 生成 `ExprNode`，求值期经 `RuleEngine.evalRule()` 解析。
- `RuleSuite` 装配 `RuleEngine` + `NodeBuilder`。构建整套时先收集全部复合规则再递归编译，使复合规则可以引用后定义的复合规则。
- DSL 运算符：`->` 串行、`=>` 并行、`?:` 条件、`&&`/`||` 有序短路、`some(min,max,...)` 命中数量约束、`loop(max,condition,action)` 有界循环；`all(...)`/`any(...)` 复用与/或语义并保留独立身份；全部可递归嵌套。`_` 表示区间某侧不限（如 `some(2,_,a,b,c)`）；`∅` 只是结构占位，从不承载业务逻辑。
- 对应节点：逻辑节点 `AndNode`/`OrNode`/`AllNode`/`AnyNode`/`NotNode`/`SomeNode`，条件节点 `CaseNode`，执行结构 `SerNode`/`ParNode`/`LoopNode`，规则引用 `ExprNode`，复合规则根 `CompositeNode`。
- **结果契约** —— 职责分离：`EvalResult.matched` 表示匹配/控制状态，`EvalResult.result` 只承载节点有明确语义的业务返回值。不要把诊断/状态塞进 `result`；未与维护者对齐前，不加 `EvalResult`/`RuleResult`/`NodeResult` 字段，也不改其序列化。
- 逻辑节点解释子节点的 `matched` 并短路。执行节点（`SerNode`/`ParNode`）运行子节点（串行 / 并行后等待），正常完成时返回 `result=null, matched=true`，从不因子节点的 `matched` 中断或聚合。规则不匹配不是执行失败；异常正常向上传播。
- **有界循环** `LoopNode`：每轮清空本轮叶子缓存作用域 → 求值 `condition`，不命中即退出 → 命中则求值 `action` → 达到 `max` 退出。条件与动作共享同一轮作用域，下一轮重算使条件能读到动作更新后的 `$$`；循环实际求值后使父作用域缓存失效，避免循环外复用过期结果。叶子结果缓存由 `EvalCacheScope` 分层管理。
- **模板参数**：原子规则可声明 `$args`，通过 `ruleId("""{...}""")` 为每个节点绑定 JSON。运行时恰好暴露 `$`（输入）、`$$`（共享可变上下文）、`$args`（本节点参数）。ANTLR 只识别三引号参数块的安全边界；`RuleArguments` 在 `ExprNode` 构造时一次性解析为规范化 JSON。缓存键 = `ruleId + 规范化 JSON`（对象键递归排序、数组顺序保留），相同参数组合在一次顶层执行内只求值一次。

## UI 树模型（`mosika-ui`）

两个递归域：**Flow**（宿主）和 **Rule**（内嵌子语言），只通过 `FlowNode.rule` 单向连接，Rule 不能承载或展开 Flow 子树。

```text
Flow = ActionNode(rule, next?) | ConditionNode(rule, next?)
     | SerialNode(Flow+) | ParallelNode(Flow+)
     | DecisionNode(ConditionNode+, default?) | LoopNode(max, rule, next)
Rule = AtomicNode(expr, args?) | BooleanNode(negative)
     | LogicNode(&&|||, Rule+) | MatchNode(minHits, maxHits, Rule+)
```

- 结构显式声明，不从布局/坐标/连线方向推断。`SerialNode(ParallelNode(a,b),ParallelNode(c,d))` 直接表示“先跑完第一组并行，再跑第二组”。保留显式串行节点，不用“竖=串、横=并”的布局约定编码语义（那会强制汇合点、把树退化成 DAG）。
- `SerialNode`/`ParallelNode`/`DecisionNode`/`LoopNode` 是各自子树的一等虚拟根；同类子树可互相替换（任意 `FlowNode` 作分支，任意 `RuleNode` 作子规则）。
- `AtomicNode.expr` 保存稳定的 `RuleDefinition.ruleId` 引用；`name` 是可编辑的展示名（初值可来自 `RuleDefinition.desc`），是节点 UI 元数据，不参与 `ruleExpr()`/求值。
- `UINodeAdapter` 只提供 `toRule()` —— UI AST 到 `RuleNode` 的单向编译，丢弃 `name` 等编辑元数据，不能反向恢复 UI 树。
- 改任一节点都要保持一致：`toRule()` 编译、`TreeNode.visit()` 的全部递归边、`NodeTypeResolver` 的 JSON 类型映射，以及“UI → JSON → UI → Rule”的结构/语义等价。

完整节点字段、遍历、校验、单向编译映射与画布交互见 [`docs/规则编排交互设计.md`](docs/规则编排交互设计.md)。

## Web 层（`mosika-web`）

原子规则与业务场景存在独立主键的两张表，进入 Core 统一 `ruleId` 命名空间时加稳定前缀（参考实现：原子 `r<id>`、场景 `f<id>`），不得假设两表数字 ID 不冲突。`RuleTreeCompiler` 先校验 UI 树（`MAX_TREE_DEPTH=128`、`MAX_TOTAL_NODES=2000`）再单向编译为 `RuleNode` + 规范化 JSON + DSL；`RuleSuiteManager` 装配并热刷新全局唯一的 `RuleSuite`。UDF 是进程内可执行代码，不是不可信配置；`sys` 命名空间保留给内置。

---

# 协作硬约束

## 一、修改范围

- 只改任务明确要求的方法。其他方法、其他文件，**一行都不许动**。
- 认为有改进空间的地方，在回复里说明，**不要直接改**。
- 不要顺手做重构、重命名、抽接口、提公共方法。

## 二、参数校验：先判断边界

加校验前先回答：**这个参数从哪来的？**

**信任边界内 —— 不加任何校验：**
- private / package-private 方法
- 同模块内部调用的 public 方法
- 构造器接收 varargs（JVM 保证非 null）
- 参数由本仓库其他代码构造并传入

理由：调用方在同一仓库里，传错就是 bug，应在测试里暴露，不靠运行时兜住。下一行解引用照样 NPE，栈顶就在出错位置。

**信任边界上 —— 才加校验：**
- HTTP / RPC 入口（mosika-web 的 Controller 层）
- 解析外部输入：DSL 文本、JSON 参数、数据库读出的规则定义
- 值被存进字段、隔很久才解引用（此时 NPE 栈里已无源头信息）

**具体禁止：**
- 禁止 `Objects.requireNonNull` 出现在 core 的构造器和内部方法里
- 禁止对 varargs 参数做 null 检查
- 禁止同一个值在调用链上被反复检查
- 禁止为“防御”而加 try-catch 吞异常

## 三、集合与不可变

- **禁止 `List.copyOf` / `Set.copyOf` / `Map.copyOf`**，除非明确说明这是对外 API 边界且能说清被篡改的具体场景。
- 禁止把可变类改成不可变。
- 禁止加防御性拷贝。
- 禁止给已有的可变字段套 `unmodifiableList` 之类的包装。

`RuleNode` 的实现类（`AndNode` / `OrNode` / `NotNode` / `CompositeNode` 等）是**构建器语义**，节点内部集合就是要可变的。

## 四、方法契约：绝对不许改

以下任何一条都属于破坏性变更，禁止在未明确要求时改动：

- 改返回值语义。特别是：**返回 `this` 的方法必须继续返回 `this`**，不许改成返回新对象。`RuleNode.and()` / `or()` 是原地追加 + 返回自身，这是接口契约，所有实现类必须一致。
- 改方法签名、参数顺序、返回类型。
- 把原地修改改成拷贝-新建。
- 改字段可见性。

`final` 只用于“防止字段引用被重新赋值”，不要连带把集合内容也改成不可变——这是两件事。

不要从方法名推断语义，例如 `ParNode.next()` 是协变重写、追加的是并行节点而非串行节点。

## 五、性能约束

`eval()` 及其调用链是求值热路径，规则解析期的节点组装也是高频路径。

- 链式调用的方法里禁止 O(n) 拷贝——`and()` 被调 n 次就是 O(n²)。
- 热路径上新增任何堆分配，必须在回复里说明理由。
- 不要为了“看起来干净”引入 Stream / Optional 替换现有循环。

## 六、代码风格

- Java，4 空格缩进，包名在 `com.nianien.mosika` 下；类型 UpperCamelCase，方法 lowerCamelCase，常量 UPPER_SNAKE。
- 不要加 `// 遍历所有节点` 这种复述代码的注释。
- 不要为单一实现抽接口。
- 不要加工厂、建造者、策略类等未被要求的抽象层。
- 不要动 lombok 注解。
- 提交信息用简短中文主题，一次提交一个关注点；PR 说明受影响模块、语义变更和验证命令（UI 树改动附代表性 DSL/JSON/结构）。

## 七、页面文案

- 未明确要求时，不在页面标题下添加解释性副标题、统计说明或教学文案。
