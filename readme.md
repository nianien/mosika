# Mosika

<p align="center">
  <strong>让规则图成为可编译、可执行、可解释的业务语言</strong><br>
  <sub><strong>模型驱动的可视化规则流语言与执行内核</strong></sub><br>
  <sub>JDK 21 · Rhino · ANTLR4</sub>
</p>

<p align="center">
  <a href="#为什么是-mosika">技术价值</a> ·
  <a href="#规则流是什么样的">核心能力</a> ·
  <a href="#模板参数化规则">模板参数</a> ·
  <a href="./docs/规则编排交互设计.md">编排交互</a> ·
  <a href="./docs/规则模型核心设计.md">内核设计</a> ·
  <a href="./CHANGELOG.md">更新日志</a> ·
  <a href="./docs/README.md">完整文档</a> ·
  <a href="./mosika-web/src/main/resources/static/ui/index.html">参考 UI 源码</a>
</p>

## 为什么是 Mosika

> **真正稀缺的不是“能拖拽”，而是从画布到运行时始终只有一套语义。**

Mosika 使用可序列化 UI AST 保存编辑结构，并单向编译为 Core 的递归执行树。画布结构不是展示结果，而是串行、并行、判断和决策的明确语义来源；产品编排在 Core 边界统一转换为命名复合规则。

仓库包含与 Web 框架解耦的 Core、可序列化 UI AST 和参考 Web 服务。编辑、持久化、编译与进程内执行使用同一套节点和规则契约。

### 核心与参考实现的边界

> **`mosika-web` 只是 UI 与控制面的参考实现，不是 Mosika 的核心。**

Mosika 的规则语义和执行能力全部来自 `mosika-core`；`mosika-udf` 集中存放 Java UDF 实现；`mosika-spring-boot-starter` 负责把 Spring 容器中的 `@Udf` Bean 转换为 Core 定义；`mosika-ui` 只提供可序列化的 UI AST 及其到执行树的单向编译能力，也不包含真正的页面。可选的 `mosika-web` 使用 Spring Boot、SQLite 和静态页面演示如何把这些模块组合成一套可运行的规则管理与可视化编排界面，主要用于能力展示、集成验证和独立体验。

接入 Mosika 内核不需要依赖 `mosika-web`。参考 Web 不定义新的规则语义，不替代 Core/UI 的库制品，也不代表公司内部生产级产品的完整控制面、平台集成和生态能力。

### 设计初衷：让稳定语义沉淀，让业务策略自由组合

> **原子规则沉淀业务事实，规则流表达产品策略。**

Mosika 从一开始就把**规则的生成**与**规则的编排**作为两个独立生命周期：规则生产侧负责把领域知识封装为具有稳定业务语义、可以独立测试和复用的最小单元 `RuleDefinition`；产品与业务侧基于这些原子规则的稳定标识组织规则流，表达串行、并行、判断和决策，而不需要感知底层表达式或实现细节。

```text
规则生产：领域知识 ──> RuleDefinition(ruleId, expression, desc)
                              │
                              │ 稳定 ruleId 引用
                              ▼
产品编排：业务策略 ──> 命名规则流(串行 / 并行 / 判断 / 决策)
```

这条边界让规则实现可以独立演进，让同一业务语义可以被多个产品复用，也让产品策略能够以编排方式快速变化。技术团队沉淀可靠的规则能力，产品和业务专注表达“在什么场景下，按什么顺序，做什么决策”。

### 核心主张

| 核心主张 | Mosika 的回答 |
| --- | --- |
| **生成与编排解耦** | 原子规则是具有稳定业务语义的最小单元；规则流只引用稳定 `ruleId`，让规则实现与产品策略分别演进 |
| **一条规则，多组参数** | 原子规则可以声明 `$args` 模板参数，同一个 `ruleId` 在一棵规则树中按节点绑定不同 JSON 参数，无需为阈值差异复制规则定义 |
| **图即源代码** | UI AST 及其 JSON 是编辑和持久化的唯一事实来源，不从执行树反推模型，也不存在与画布分离的第二套流程语义 |
| **规则与流程分域** | `Rule` 只负责求值，`Flow` 负责执行；`FlowNode.rule` 单向嵌入规则子树，从类型上阻止规则反向携带流程关系 |
| **作用域显式可组合** | 串行、并行、判断和决策都是可递归嵌套的结构节点，不依赖坐标、连线方向、隐式汇合或通用 DAG 推断 |
| **先编译，后生效** | 定义先经过规模校验、结构校验和完整编译；参考 Web 在事务提交后替换当前 `RuleSuite` 引用，不向执行请求暴露半成品 |
| **结果语义不污染** | `matched`、业务 `result` 和递归执行详情各司其职，为在线测试和问题诊断提供稳定契约 |
| **配置与执行隔离** | 规则脚本和 UDF 在 `RuleEngine` 构造期编译并共享，每次顶层求值创建独立 `RuleVisitor` 和脚本作用域 |

### 它不是普通规则框架的“可视化版”

- 组件编排从代码组件和共享上下文出发；Mosika 从可持久化、可编译的业务规则模型出发。
- 推理引擎从事实匹配、规则激活和冲突消解出发；Mosika 从确定性的树形作用域和执行顺序出发。
- 通用流程画布从节点、边和拓扑关系出发；Mosika 的 Flow 子树天然定义分支、顺序和局部汇合，不需要多入边或隐藏图算法。

Mosika 不试图替代 BPM、通用 DAG 或企业平台基础设施。它专注做好一件事：**让一份规则流语义在编辑、持久化、编译、测试和执行之间保持一致。**

## 规则流是什么样的

<table>
  <tr>
    <td width="50%" align="center">
      <strong>串行</strong><br>
      <code>a-&gt;b-&gt;c-&gt;d</code><br><br>
      <img src="./docs/images/serial.svg" alt="串行执行：a、b、c、d 依次执行" width="100%">
    </td>
    <td width="50%" align="center">
      <strong>并行</strong><br>
      <code>a-&gt;(b=&gt;c)-&gt;d</code><br><br>
      <img src="./docs/images/parallel.svg" alt="并行执行：b、c 同时执行并等待完成" width="100%">
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <strong>完整条件</strong><br>
      <code>(a?b:c)-&gt;d</code><br><br>
      <img src="./docs/images/conditional.svg" alt="完整条件：a 命中执行 b，否则执行 c" width="100%">
    </td>
    <td width="50%" align="center">
      <strong>半条件</strong><br>
      <code>a?(b-&gt;d)</code><br><br>
      <img src="./docs/images/half-conditional.svg" alt="半条件：a 命中后执行 b 和 d" width="100%">
    </td>
  </tr>
  <tr>
    <td colspan="2" align="center">
      <strong>有序多分支</strong><br>
      <code>a?b:(c?d:e)</code><br><br>
      <img src="./docs/images/multi-branch.svg" alt="有序多分支：命中首个条件后停止，并支持默认分支" width="56%">
    </td>
  </tr>
</table>

串行、并行、条件、决策和有界循环都是显式结构节点，可以递归嵌套；布局方向只负责展示，不参与执行语义。

有界循环使用 `loop(max, condition, action)`：每轮先判断条件，命中后执行动作，条件未命中或达到最大轮数时退出。条件和动作在同一轮共享求值缓存，下一轮重新计算，循环也可以嵌套。

```text
loop(3,shouldRetry,rebuildContent->qualityCheck)
```

## 模板参数化规则

> **一条原子规则定义可以作为模板，在同一规则树中绑定不同参数重复使用。**

过去只有阈值不同的判断需要复制成多条规则：

```text
r1 = $.input > 100
r2 = $.input > 200
r3 = $.input > 300
```

现在可以只定义一条模板规则：

```text
threshold = $.input > $args.limit
```

在规则 ID DSL 的原子规则节点上绑定 JSON 参数：

```text
threshold("""{"limit":100}""") || threshold("""{"limit":300}""")
```

执行环境保持三个稳定入口：

| 名称 | 含义 |
| --- | --- |
| `$` | 本次执行的业务输入 |
| `$$` | 本次执行共享的可变上下文 |
| `$args` | 当前原子规则节点绑定的模板参数 |

ANTLR4 只负责识别三引号参数块的安全边界，`ExprNode` 在构造时通过 `JsonUtils` 把参数解析为 `Map<String,Object>`，并用规范化 JSON 生成稳定的 `expr()`；JavaScript 源码不做字符串替换。`NodeBuilder` 不缓存原子节点或参数变体，单次顶层执行由 `RuleVisitor` 直接按 `ExprNode.expr()` 复用叶子结果：对象字段在所有嵌套层级递归排序，数组元素顺序保留。相同参数组合只真正求值一次，不同参数组合分别求值；每次出现仍保留各自的执行节点、结果和动态描述。未传参数的规则保持原有语义，并收到空的 `$args` 对象。

完整语法、执行缓存和使用边界见[规则模型核心设计](./docs/规则模型核心设计.md#7-模板参数化)。

## 完整 UI 树

<p align="center">
  <img src="./docs/images/ui-tree.svg" alt="Mosika 完整规则流 UI 树：串行、并行、决策、判断与动作递归组合" width="100%">
</p>

<p align="center"><sub>Flow 是主递归域，Rule 通过判断节点嵌入；规则子树与命中流程保持明确边界。</sub></p>

## 从定义到执行

<p align="center">
  <img src="./docs/images/definition-to-execution.svg" alt="Mosika 从规则定义和 UDF 到 RuleSuite、RuleNode 与执行结果的链路" width="100%">
</p>

- `RuleDefinition.ruleType=0` 描述 JavaScript 原子规则，`ruleType=2` 描述规则 ID DSL 复合规则。
- `RuleSuite` 只装配规则和 Java/JS UDF；产品层规则流在边界处编译为复合规则。
- `RuleEngine` 注册全部规则并预编译原子 JavaScript，是规则注册状态的唯一事实来源；`NodeGenerator` 只接收复合规则表，不维护第二份规则 ID 集合。
- 裸 `ruleId` 在命中复合规则定义时递归编译为 `CompositeNode`，保留完整执行详情并统一检测循环引用；复合规则只按唯一 `ruleId` 识别和复用，不存在 `f1("""...""")` 形式的参数化复合规则变体，参数只属于其内部原子规则调用。
- 规则 ID DSL 使用 `&&`、`||` 表达有序短路；`AllNode` 继承 `AndNode`，`AnyNode` 继承 `OrNode`，使 `all(...)`、`any(...)` 复用当前求值实现并保留独立语法身份；`some(min,max,...)` 表达命中数量约束；`loop(max,condition,action)` 表达有界循环。
- `matched` 表达匹配或控制状态，`result` 只传递节点具有明确语义的业务返回值。
- 原子规则可以使用 `$args` 声明模板参数，并通过 `ruleId("""{...}""")` 在 DSL 节点上绑定 JSON 对象；不同参数组合独立求值，结构相同的参数复用结果。

## 模块边界

| 模块 | 职责 | 不负责 |
| --- | --- | --- |
| `mosika-core` | DSL、规则树、执行上下文、求值、UDF、命名原子/复合规则 | 页面、持久化、Flow 产品模型与业务场景映射 |
| `mosika-udf` | 可由 Starter 扫描注册的 Java UDF 实现 | Spring Bean 扫描、数据库 UDF 管理、规则套件生命周期 |
| `mosika-spring-boot-starter` | 自动发现并注册 `@Udf` Bean，提供 `SpringUdfLoader` | 规则来源、`RuleSuite` 生命周期、数据库 UDF 管理 |
| `mosika-ui` | 可序列化 UI AST，单向编译为 `RuleNode` | 页面布局、Web 依赖、从执行树反推 UI |
| `mosika-web` | 可选的 UI/控制面参考实现：REST、SQLite、规则管理和可视化编辑页面 | 规则语义、执行内核、替代 Core/UI 库制品、代表完整生产级平台 |

## 运行

要求 JDK 21。

```bash
mvn clean test
```

启动管理界面：

```bash
./scripts/mosika.sh start
```

访问：

- 产品首页：<http://127.0.0.1:8080/>
- 命名空间：<http://127.0.0.1:8080/namespaces>
- 业务场景：<http://127.0.0.1:8080/scenes>
- 原子规则库：<http://127.0.0.1:8080/rules>
- UDF 注册中心：<http://127.0.0.1:8080/udfs>
- 规则流画布：`http://127.0.0.1:8080/flow/{id}`

在 UDF 页面中可以注册带参数的 JavaScript 函数，例如把 `function bindCitations(target) { ... }` 注册为 `content.generation.bindCitations`，随后在普通规则表达式中直接调用 `content.generation.bindCitations($)`。创建、编辑和启用会经过内核编译校验，成功提交后刷新当前命名空间的 `RuleSuite`；`sys` 命名空间为内置能力保留。页面同时展示 Spring 扫描得到的 Java UDF 和数据库中的 JavaScript UDF：Java UDF 对所有命名空间可用且只读，可查看暴露的方法签名和说明；JavaScript UDF 按命名空间隔离并可编辑。

UDF 是进程内可执行代码，不是面向不受信任用户的普通配置。参考 Web 默认只监听本机且不提供生产级鉴权；对外或多租户部署时，必须在平台层增加身份认证、分级授权、审核发布和操作审计。

仓库同时提供一套可重复导入的[内容生成领域演示数据](./mosika-web/src/main/resources/demo/README.md)：用 3 个领域 UDF、64 条原子规则和 7 条相互复用的规则流模拟素材证据链、内容路由、深度文章/快讯/营销文案生产、多渠道适配和分级发布门禁。它用于展示 Mosika 内核如何承载真实领域编排，不改变 `mosika-web` 仅作为参考 UI 与控制面的定位。

开发与运维命令：

```bash
./scripts/mosika.sh dev
./scripts/mosika.sh status
./scripts/mosika.sh logs
./scripts/mosika.sh restart
./scripts/mosika.sh stop
```

环境变量、运行目录和分模块验证命令见[运维与开发](./docs/运维与开发.md)。

## 进一步阅读

- [文档索引](./docs/README.md)：全部有效文档及其适用边界。
- [规则模型核心设计](./docs/规则模型核心设计.md)：设计理念、DSL 语法、节点体系、引擎装配、求值缓存、UDF 内核能力与内置库、模板参数。
- [规则编排交互设计](./docs/规则编排交互设计.md)：规则定义与编排解耦、递归树统一串/并/分支/循环、UI 单向编译为 DSL、在线测试与执行路径渲染、版本生命周期。
- [运维与开发](./docs/运维与开发.md)：工程结构、构建测试、服务脚本、部署与 Java/JavaScript UDF 的定义、装配和使用。
