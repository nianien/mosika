# UITree 设计文档

> 适用模块：`mosika-ui`
>
> 状态：已落地的当前设计基线
>
> 更新时间：2026-08-13
>
> 核心目标：用正交分层表达可视化执行结构与规则表达式，避免通过继承复用不属于当前节点的字段和关系。
>
> 页面交互、版本生命周期和路由说明见[UI 树与 Web 规则编辑器](./规则编辑器.md)。

## 1. 设计出发点

UITree 同时承担两项职责：

1. 保存前端可以稳定序列化、编辑和渲染的执行结构；
2. 保存执行节点绑定的规则表达式，并将整棵树编译为内核 `RuleNode`。

这两项职责相关，但不是同一个递归域。执行结构回答“流程怎样组织”，规则表达式回答“当前节点执行或判断什么”。如果把二者放进同一继承链，就会让分支节点继承原子表达式，让组合规则继承流程关系，最终只能依靠运行时判断排除错误能力。

UITree 因此不追求用一棵继承树表达所有概念，而是将类型识别、命名、执行拓扑和规则表达式拆成相互正交的层次。每一层只引入一个维度，具体节点通过继承和组合共同获得完整语义。

## 2. 总体结构

```text
TypeNode
└─ NameNode(name)
   ├─ TreeNode(next)
   ├─ UINode
   │  ├─ FlowNode<R extends RuleNode>(rule, next)
   │  │  ├─ ANode<RNode>
   │  │  └─ CNode<BNode>
   │  └─ BranchNode<N extends UINode>(branches)
   │     ├─ DNode<CNode>(defaultBranch)
   │     ├─ SNode<UINode>
   │     └─ PNode<UINode>
   └─ RuleNode(ruleExpr)
      └─ RNode(expr, args)
         └─ BNode(negative)
            └─ LNode(rules)
               └─ HNode(minHits, maxHits)
```

整个模型包含两个递归域：

```text
执行递归域：UINode
规则递归域：RuleNode
```

两个递归域只通过下面这一条单向关系连接：

```text
FlowNode.rule -> RuleNode
```

规则节点不能引用 `UINode`，因此规则表达式不会反向携带流程关系。

## 3. 四个正交维度

### 3.1 类型识别：TypeNode

`TypeNode` 是 JSON 多态序列化标记，不保存业务字段，也不规定节点行为。

具体类型由 `NodeTypeResolver` 根据类名解析：

```text
T / A / C / D / S / P / R / B / L / H
```

`type` 是机器识别信息，不需要作为另一个可修改字段保存在节点中。这样可以避免对象实际类型与字段值不一致。

### 3.2 节点命名：NameNode

`NameNode` 只保存：

```text
name
```

所有需要被识别、展示或编辑名称的节点共享这一属性：

- `TreeNode` 可以保存整棵树的名称；
- `UINode` 可以保存画布执行节点名称；
- `RuleNode` 可以保存规则表达式名称。

统一的是“节点名称”的字段语义，不是强制不同对象共用同一个值。例如：

```text
CNode.name       = 风险判断
CNode.rule.name  = 高风险内容条件
```

前者描述画布步骤，后者描述该步骤绑定的规则。

### 3.3 执行拓扑：UINode

`UINode` 只表示可参与执行结构编排的节点。根据拓扑形态分为两类：

```text
FlowNode    单规则、单出口
BranchNode  无规则、多子树
```

两者是兄弟关系。`BranchNode` 不继承 `FlowNode`，因此不会获得没有语义的 `rule` 或 `next`。

### 3.4 规则表达式：RuleNode

`RuleNode` 只负责生成规则 DSL：

```java
public abstract String ruleExpr();
```

规则递归域不负责流程跳转。其表达能力逐层增加：

- `RNode`：规则引用或原子调用，保存 `expr/args`；
- `BNode`：在规则基础上增加整体取反；
- `LNode`：增加有序子规则列表，表达 `&&/||`；
- `HNode`：在组合规则基础上增加命中数量上下界。

`args` 只对原子规则调用有意义；`LNode/HNode` 不使用继承得到的 `args`。

## 4. FlowNode：单规则、单出口

`FlowNode` 保存：

```text
rule : RuleNode
next : UINode?
```

它描述一个带规则载荷、至多拥有一个执行出口的画布节点。`ANode` 和 `CNode` 具有相同结构，但解释方式不同。

### 4.1 ANode

`ANode` 表示动作：

```text
执行 rule
然后执行 next
```

普通动作链直接通过 `next` 表达：

```text
A1 -> A2 -> A3
```

对应对象关系：

```text
A1.next = A2
A2.next = A3
A3.next = null
```

不需要为每一条顺序边增加 `SNode`，也不需要透明包装节点。

### 4.2 CNode

`CNode` 表示条件：

```text
判断 rule
命中时执行 next
未命中时结束当前条件分支
```

因此 `CNode.next` 的语义固定为“命中分支”，不是动作节点意义上的无条件后继。

```text
C(rule=c1)
└─ true -> A1
```

外层节点根据具体类型解释同名的单出口关系：

```text
ANode.next = then
CNode.next = matched
```

## 5. BranchNode：无规则、多子树

`BranchNode` 基类只保存：

```text
branches : List<UINode>
```

它表示完整的结构运算符，不是线性流程中的普通步骤，因此没有 `rule`，也绝不能拥有 `next`。具体结构节点可以增加自身特有的子树关系，例如 `DNode.defaultBranch`，但不能把这种关系提升为所有结构节点共有的后继。

如果一个组合结构需要和其他完整子树继续组合，由外层 `SNode` 表达，而不是给组合结构增加后继字段。

### 5.1 DNode

`DNode` 保存有序条件分支和一个可选默认分支：

```text
DNode
├─ branches[0]      : C(c1 -> A1)
├─ branches[1]      : C(c2 -> A2)
└─ defaultBranch    : A3
```

执行时按列表顺序判断，命中第一个条件后执行该 `CNode.next`，并停止判断后续分支。

所有条件均未命中时执行 `defaultBranch`：

```text
defaultBranch : UINode
允许类型       : ANode / DNode / PNode / SNode
```

默认分支不是条件，因此不能使用 `CNode`。字段使用 `UINode` 是为了容纳不同形态的完整执行子树，不表示任意 `UINode` 都符合默认分支语义。

`DNode` 至少需要两个执行出口。出口数量等于条件分支数量加上非空默认分支，因此一个条件分支配合一个默认分支也是合法决策。

### 5.2 SNode

`SNode` 按列表顺序执行若干完整 `UINode` 子树：

```text
S[D(...), P(...), A(...)]
```

它用于表达组合结构之间的显式顺序，不替代 `ANode.next` 所表达的普通动作链。

### 5.3 PNode

`PNode` 并行执行若干完整 `UINode` 子树，并等待所有分支完成。

`branches` 的列表顺序用于稳定编辑、遍历和序列化，不表示执行先后。

## 6. TreeNode

`TreeNode` 是序列化、遍历和编译入口，不属于动作或条件节点：

```text
TreeNode
└─ next : UINode
```

默认入口是绑定 `Constants.NOP` 的 `ANode`，用于表示一棵尚未配置业务步骤的有效空树。

根节点不参与动作引用收集，也不携带规则载荷。

## 7. 字段归属

| 字段 | 所属层级 | 含义 |
| --- | --- | --- |
| `name` | `NameNode` | 人类可读的节点名称 |
| `rule` | `FlowNode` | 当前动作或条件绑定的规则表达式 |
| `next` | `FlowNode`、`TreeNode` | 单出口关系，语义由节点类型决定 |
| `branches` | `BranchNode` | 有序的完整子树列表 |
| `defaultBranch` | `DNode` | 所有条件均未命中时执行的默认子树 |
| `expr` | `RNode` | 原子引用或规则运算标识 |
| `args` | `RNode` | 原子调用参数 |
| `negative` | `BNode` | 对规则表达式整体取反 |
| `rules` | `LNode` | 有序子规则列表 |
| `minHits/maxHits` | `HNode` | 命中数量范围 |

不存在以下字段：

```text
BranchNode.next
DNode.action
CNode.action
JNode
label
```

## 8. 可视化映射

前端布局只需要识别两种拓扑：

| Java 类型 | 可视化形态 |
| --- | --- |
| `FlowNode` | 一个规则区域和一个单出口 |
| `BranchNode` | 一个结构节点和多个子树出口 |

具体节点只改变标题、端口说明和内部属性表单：

| 节点 | 单出口或分支语义 |
| --- | --- |
| `A` | 完成后执行 |
| `C` | 条件命中后执行 |
| `D` | 有序互斥条件分支和一个可选默认分支 |
| `S` | 有序执行子树 |
| `P` | 并行执行子树 |

节点位置、折叠状态和临时选中状态属于前端编辑会话，不进入 UITree 领域模型。

## 9. 编译映射

UITree 单向编译为内核执行节点：

| UITree | 内核节点 |
| --- | --- |
| `ANode(rule)` | 解析 `rule.ruleExpr()` |
| 连续 `ANode.next` | `SerNode` |
| `CNode(rule, next)` | `CaseNode(condition, matched)` |
| `DNode` | 以 `defaultBranch` 为最终 false 分支，按条件顺序反向嵌套 `CaseNode` |
| `SNode` | `SerNode` |
| `PNode` | `ParNode` |

编译只读取执行语义，不修改 UITree，也不把 `name` 传递到内核。

## 10. 遍历和校验

统一遍历关系如下：

```text
TreeNode   -> next
FlowNode   -> rule, next
BranchNode -> branches
DNode      -> defaultBranch
LNode      -> rules
```

结构校验至少保证：

- `TreeNode.next` 存在；
- 每个业务 `FlowNode.rule` 存在；
- `SNode/PNode` 至少包含一个子树；
- `DNode` 的条件分支和默认分支合计至少形成两个出口；
- `DNode.defaultBranch` 只能是 `ANode/DNode/PNode/SNode`；
- `LNode` 使用 `&&/||` 且至少包含两个子规则；
- `HNode` 的命中范围合法；
- 整棵树不存在重复引用或循环引用，并满足规模限制。

## 11. 核心不变量

1. `RuleNode` 不引用 `UINode`。
2. `BranchNode` 不拥有 `rule` 或 `next`。
3. `FlowNode` 只拥有一个规则载荷和一个出口。
4. `DNode.branches` 的直接子节点只能是 `CNode`，`defaultBranch` 只能是 `ANode/DNode/PNode/SNode`。
5. `SNode/PNode` 的直接子节点是完整 `UINode` 子树。
6. 普通动作顺序使用 `ANode.next`，组合子树顺序使用 `SNode.branches`。
7. `type` 用于机器识别，`name` 用于人类展示，二者不互相替代。
8. JSON 只读写当前模型，不保留旧节点结构兼容层。
