# 仓库协作指南

## 项目结构

本项目是基于 JDK 21 的 Maven 多模块规则引擎。

- `mousika-core/` 是纯内核，负责 DSL 解析、规则语法树、执行上下文、规则求值和 UDF；ANTLR 语法位于 `src/main/antlr4/Rule.g4`。
- `mousika-ui/` 不包含真正的页面或布局，只把内核语法树转换为前端容易序列化、递归遍历和渲染的数据结构。
- 生产代码和测试分别位于各模块的 `src/main/java`、`src/test/java`；不要修改 `target/generated-sources/` 中的生成代码。

## 核心设计理念

`RuleNode` 及其组合节点是规则语义的唯一来源。`->` 表示串行，`=>` 表示并行，条件、逻辑组合和 `hits(min,max,...)` 均可递归嵌套；`_` 表示命中区间的一侧不设边界，例如 `hits(2,_,a,b,c)`。`∅` 只用于稳定表达 `SNode`、`PNode` 等结构，不参与业务计算。

修改前必须沿实际链路核对 `Rule.g4`、`DefaultRuleVisitor`、具体 `RuleNode.eval()`、`UINodeAdapter` 和回归测试。不要仅凭方法名推断语义：例如 `ParNode.next()` 是协变重写，含义是追加并行节点，并非创建串行节点。保持现有组合模型，避免为兼容旧实现增加重复语法树或适配层。

## UI 树与递归约束

UI 节点表达结构而非业务语义或页面布局：`ANode.next` 表示后继，`SNode/PNode.branches` 表示串行/并行子结构，`CNode.action` 表示命中分支，`DNode` 保存有序条件分支及默认动作，`JNode.rule` 可包含由 `RNode`、`LNode`、`HNode` 组成的递归规则树。这些节点可以相互嵌套，前端自行决定横排、竖排、折叠或展开；不得因画布空间改变树的层级和 DSL 语义。

修改节点时必须同步检查：双向转换 `fromRule()/toRule()`、`TreeNode.visit()` 的全部递归边、`NodeTypeResolver` 的 JSON 类型映射，以及“规则 → UI → JSON → UI → 规则”的往返一致性。不要重新引入已合并删除的 `tree2`。

## 构建与测试

- `mvn clean test`：重新生成 ANTLR 代码并运行全部测试。
- `mvn -pl mousika-core test`：只验证内核。
- `mvn -pl mousika-ui -am test`：验证 UI 树及其内核依赖。
- `mvn clean package`：测试并生成各模块 JAR。

解析、执行、并发上下文或树转换的修改必须增加针对性回归测试。测试类使用 JUnit 5，命名为 `*Test`。

## 编码与提交

Java 使用四空格缩进，包名保持在 `com.skyfalling.mousika` 下；类、方法、常量分别使用大驼峰、小驼峰和全大写下划线命名。保持相邻代码的导入和注释风格，不引入无必要的抽象或依赖。

提交信息使用简短中文主题，一个提交聚焦一个问题。合并请求需说明影响模块、语义变化、验证命令；涉及 UI 树时附代表性 DSL、JSON 或结构示例。
