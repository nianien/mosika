/**
 * Mousika UI 树的语义模型核心（终态：前后端统一以后端 TreeNode JSON 为唯一语义结构）。
 *
 * 该模块只负责“语义树”本身：节点工厂、异构字段的统一边视图 childEdges()、
 * 通用遍历 visit()，以及与后端 TreeNode JSON 的单向序列化/反序列化。
 * 它不持有任何编辑器瞬时状态（选中/折叠/临时 id/拖拽），那些放在 app.js 的旁路 editorState。
 *
 * 节点内存形状 == 后端 TreeNode JSON（字段名严格对齐 Jackson）：
 *   T 根        : { type:"T", expr:"",     next? }
 *   A 动作      : { type:"A", expr,        next? }
 *   C 兼容条件  : { type:"C", expr, negative, action? }
 *   J 判断      : { type:"J", expr:"J",    negative, rule, action? }
 *   S 串行      : { type:"S", expr:"S",    branches:[] }
 *   P 并行      : { type:"P", expr:"P",    branches:[] }
 *   D 决策      : { type:"D", expr:"D",    branches:[], action? }   // action = 默认分支
 *   R 原子规则  : { type:"R", expr,        negative, name? }
 *   L 逻辑与/或 : { type:"L", expr:"&&"|"||", negative, name?, rules:[] }
 *   H 命中数    : { type:"H", expr:"hits", negative, name?, rules:[], minHits?, maxHits? }
 *
 * relation 只是 childEdges() 遍历/布局时临时生成的“边视图”，不落到节点字段上：
 *   T/A.next→"next"  C.action→"action"  J.rule→"rule" J.action→"action"
 *   S/P.branches→"branch"  D.branches→"decision" D.action→"default"  L/H.rules→"rule"
 */
(function (root, factory) {
    "use strict";
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    if (root) root.MousikaTree = api;
})(typeof self !== "undefined" ? self : (typeof globalThis !== "undefined" ? globalThis : this), function () {
    "use strict";

    // 主流程递归域中可作为通用后继/分支创建的流程节点类型。
    const FLOW_TYPES = ["A", "S", "P", "D", "J"];
    // 各类型携带 negative（primitive boolean，序列化恒存在）。
    const NEGATABLE = new Set(["C", "J", "R", "L", "H"]);
    // 携带可编辑规则名 name 的规则节点。
    const NAMED = new Set(["R", "L", "H"]);
    // 后端各节点类的默认 label（= 类简名）。流程节点的“可选节点名称”映射到 UINode.label：
    // 它随 TreeNode JSON 持久化，只在编译成 RuleNode 时被丢弃，符合 label 契约。
    // label 等于类默认值时视为“未设置”，序列化时省略、backend 会补回类名。
    const DEFAULT_LABEL = {
        T: "TreeNode", A: "ANode", C: "CNode", J: "JNode",
        S: "SNode", P: "PNode", D: "DNode", R: "RNode", L: "LNode", H: "HNode"
    };
    function customLabel(type, label) {
        return label && label !== DEFAULT_LABEL[type] ? label : "";
    }

    /**
     * 把异构的命名字段（next/action/branches/rule/rules）投影成统一的有序边列表。
     * 每个边条目携带 relation 及可用于原地增删改的容器信息：
     *   单值边: { relation, node, kind:"single", field }
     *   列表边: { relation, node, kind:"list", list, index }
     */
    function childEdges(node) {
        if (!node) return [];
        switch (node.type) {
            case "T":
            case "A":
                return node.next ? [{ relation: "next", node: node.next, kind: "single", field: "next" }] : [];
            case "C":
                return node.action ? [{ relation: "action", node: node.action, kind: "single", field: "action" }] : [];
            case "J": {
                const edges = [];
                if (node.rule) edges.push({ relation: "rule", node: node.rule, kind: "single", field: "rule" });
                if (node.action) edges.push({ relation: "action", node: node.action, kind: "single", field: "action" });
                return edges;
            }
            case "S":
            case "P":
                return (node.branches || []).map((child, index) =>
                    ({ relation: "branch", node: child, kind: "list", list: node.branches, index }));
            case "D": {
                const edges = (node.branches || []).map((child, index) =>
                    ({ relation: "decision", node: child, kind: "list", list: node.branches, index }));
                if (node.action) edges.push({ relation: "default", node: node.action, kind: "single", field: "action" });
                return edges;
            }
            case "L":
            case "H":
                return (node.rules || []).map((child, index) =>
                    ({ relation: "rule", node: child, kind: "list", list: node.rules, index }));
            default:
                return [];
        }
    }

    /** 直接子节点数组（丢弃 relation，仅取节点），供只需子节点的场景使用。 */
    function childNodes(node) {
        return childEdges(node).map((edge) => edge.node);
    }

    /**
     * 深度优先遍历语义树。visitor(node, parent, relation) 返回 false 立即整体停止。
     */
    function visit(node, visitor, parent = null, relation = null) {
        if (!node) return true;
        if (visitor(node, parent, relation) === false) return false;
        for (const edge of childEdges(node)) {
            if (visit(edge.node, visitor, node, edge.relation) === false) return false;
        }
        return true;
    }

    /** 语义树 → 后端 TreeNode JSON 对象（省略 label 与所有瞬时字段；backend 会补 label 并规范化）。 */
    function serialize(node) {
        if (node == null) return null;
        const type = node.type;
        if (type === "PH") {
            // 占位节点是编辑期专用的空槽，没有后端表示；保存前应已被配置替换。
            throw new Error("存在待配置的占位节点，无法保存");
        }
        const out = { type };
        out.expr = type === "T" ? (node.expr != null ? node.expr : "") : node.expr;
        const lbl = customLabel(type, node.label);
        if (lbl) out.label = lbl;
        if (NAMED.has(type) && node.name != null && node.name !== "") out.name = node.name;
        if (NEGATABLE.has(type)) out.negative = !!node.negative;
        if (type === "H") {
            if (node.minHits != null) out.minHits = node.minHits;
            if (node.maxHits != null) out.maxHits = node.maxHits;
        }
        switch (type) {
            case "T":
            case "A":
                if (node.next) out.next = serialize(node.next);
                break;
            case "C":
                if (node.action) out.action = serialize(node.action);
                break;
            case "J":
                out.rule = serialize(node.rule);
                if (node.action) out.action = serialize(node.action);
                break;
            case "S":
            case "P":
                out.branches = (node.branches || []).map(serialize);
                break;
            case "D":
                out.branches = (node.branches || []).map(serialize);
                if (node.action) out.action = serialize(node.action);
                break;
            case "L":
            case "H":
                out.rules = (node.rules || []).map(serialize);
                break;
            default:
                break;
        }
        return out;
    }

    /** 后端 TreeNode JSON（字符串或对象）→ 语义树；忽略 label 等未知/瞬时字段。 */
    function deserialize(json) {
        const obj = typeof json === "string" ? JSON.parse(json) : json;
        return fromObject(obj);
    }

    function fromObject(o) {
        if (o == null) return null;
        const type = o.type;
        const node = { type };
        node.expr = o.expr;
        node.label = customLabel(type, o.label);
        if (NAMED.has(type) && o.name != null && o.name !== "") node.name = o.name;
        if (NEGATABLE.has(type)) node.negative = !!o.negative;
        switch (type) {
            case "T":
                node.expr = o.expr != null ? o.expr : "";
                if (o.next) node.next = fromObject(o.next);
                break;
            case "A":
                if (o.next) node.next = fromObject(o.next);
                break;
            case "C":
                if (o.action) node.action = fromObject(o.action);
                break;
            case "J":
                node.expr = "J";
                node.rule = fromObject(o.rule || { type: "R", expr: "true" });
                if (o.action) node.action = fromObject(o.action);
                break;
            case "S":
                node.expr = "S";
                node.branches = (o.branches || []).map(fromObject);
                break;
            case "P":
                node.expr = "P";
                node.branches = (o.branches || []).map(fromObject);
                break;
            case "D":
                node.expr = "D";
                node.branches = (o.branches || []).map(fromObject);
                if (o.action) node.action = fromObject(o.action);
                break;
            case "R":
                break;
            case "L":
                node.rules = (o.rules || []).map(fromObject);
                break;
            case "H":
                node.expr = "hits";
                node.rules = (o.rules || []).map(fromObject);
                if (o.minHits != null) node.minHits = o.minHits;
                if (o.maxHits != null) node.maxHits = o.maxHits;
                break;
            default:
                break;
        }
        return node;
    }

    /** 序列化为紧凑 JSON 字符串（存盘用）。 */
    function toJson(node) {
        return JSON.stringify(serialize(node));
    }

    // ---- 节点工厂（语义字段齐全，供 app.js 创建新节点） ----
    const make = {
        root: () => ({ type: "T", expr: "", next: null }),
        action: (expr) => ({ type: "A", expr, next: null }),
        cond: (expr) => ({ type: "C", expr, negative: false, action: null }),
        judge: (rule) => ({ type: "J", expr: "J", negative: false, rule: rule || make.atom("true"), action: null }),
        serial: () => ({ type: "S", expr: "S", branches: [] }),
        parallel: () => ({ type: "P", expr: "P", branches: [] }),
        decision: () => ({ type: "D", expr: "D", branches: [], action: null }),
        atom: (expr, name) => ({ type: "R", expr, negative: false, name: name || "" }),
        logic: (op) => ({ type: "L", expr: op === "||" ? "||" : "&&", negative: false, name: "", rules: [] }),
        hits: (minHits, maxHits) => ({ type: "H", expr: "hits", negative: false, name: "", rules: [], minHits: minHits ?? null, maxHits: maxHits ?? null }),
        // 编辑期占位空槽（无后端表示，保存前须被配置替换）。slot: branch|decision|default
        placeholder: (slot) => ({ type: "PH", expr: "", slot: slot || "branch" })
    };

    return {
        FLOW_TYPES,
        DEFAULT_LABEL,
        customLabel,
        childEdges,
        childNodes,
        visit,
        serialize,
        deserialize,
        toJson,
        make
    };
});
