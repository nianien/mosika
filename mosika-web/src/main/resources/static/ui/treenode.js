(function (root, factory) {
    "use strict";
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    if (root) root.MosikaTree = api;
})(typeof self !== "undefined" ? self : (typeof globalThis !== "undefined" ? globalThis : this), function () {
    "use strict";

    const FLOW_TYPES = ["ActionNode", "ConditionNode", "SerialNode", "ParallelNode", "DecisionNode", "LoopNode"];
    const RULE_TYPES = new Set(["AtomicNode", "BooleanNode", "LogicNode", "MatchNode"]);
    const NEGATABLE = new Set(["BooleanNode", "LogicNode", "MatchNode"]);
    const ARGS = new Set(["AtomicNode", "BooleanNode"]);

    function edge(relation, node, kind, owner, field, list, index) {
        return { relation, node, kind, owner, field, list, index };
    }

    function childEdges(node) {
        if (!node) return [];
        const edges = [];
        switch (node.type) {
            case "TreeNode":
                if (node.next) edges.push(edge("next", node.next, "single", node, "next"));
                break;
            case "ActionNode":
            case "ConditionNode":
            case "LoopNode":
                if (node.rule) edges.push(edge("rule", node.rule, "single", node, "rule"));
                if (node.next) edges.push(edge("next", node.next, "single", node, "next"));
                break;
            case "SerialNode":
            case "ParallelNode":
                (node.branches || []).forEach((child, index) => {
                    if (child) edges.push(edge("branch", child, "list", node, "branches", node.branches, index));
                });
                break;
            case "DecisionNode":
                (node.branches || []).forEach((child, index) => {
                    if (child) edges.push(edge("decision", child, "list", node, "branches", node.branches, index));
                });
                if (node.defaultBranch) edges.push(edge("default", node.defaultBranch, "single", node, "defaultBranch"));
                break;
            case "LogicNode":
            case "MatchNode":
                (node.rules || []).forEach((child, index) => {
                    if (child) edges.push(edge("rule", child, "list", node, "rules", node.rules, index));
                });
                break;
            default:
                break;
        }
        return edges;
    }

    function childNodes(node) {
        return childEdges(node).map((item) => item.node);
    }

    function executionEdges(node) {
        return childEdges(node).filter((item) => item.relation !== "rule");
    }

    function hasArgs(nodeOrType) {
        const type = typeof nodeOrType === "string" ? nodeOrType : nodeOrType?.type;
        return ARGS.has(type);
    }

    function visit(node, visitor, parent = null, relation = null) {
        if (!node) return true;
        if (visitor(node, parent, relation) === false) return false;
        for (const item of childEdges(node)) {
            if (visit(item.node, visitor, node, item.relation) === false) return false;
        }
        return true;
    }

    function serialize(node) {
        if (node == null) return null;
        const type = node.type;
        if (!["TreeNode", ...FLOW_TYPES, ...RULE_TYPES].includes(type)) {
            throw new Error(`不支持的节点类型: ${type}`);
        }
        const out = { type, name: node.name || "" };

        switch (type) {
            case "TreeNode":
                if (node.next) out.next = serialize(node.next);
                break;
            case "ActionNode":
            case "ConditionNode":
            case "LoopNode":
                if (type === "LoopNode") out.max = node.max;
                if (node.rule) out.rule = serialize(node.rule);
                if (node.next) out.next = serialize(node.next);
                break;
            case "SerialNode":
            case "ParallelNode":
                out.branches = (node.branches || []).map(serialize);
                break;
            case "DecisionNode":
                out.branches = (node.branches || []).map(serialize);
                if (node.defaultBranch) out.defaultBranch = serialize(node.defaultBranch);
                break;
            case "AtomicNode":
            case "BooleanNode":
                out.expr = node.expr;
                if (ARGS.has(type) && node.args != null && node.args !== "") out.args = node.args;
                if (NEGATABLE.has(type)) out.negative = !!node.negative;
                break;
            case "LogicNode":
                out.expr = node.expr;
                out.negative = !!node.negative;
                out.rules = (node.rules || []).map(serialize);
                break;
            case "MatchNode":
                out.expr = "some";
                out.negative = !!node.negative;
                out.rules = (node.rules || []).map(serialize);
                if (node.minHits != null) out.minHits = node.minHits;
                if (node.maxHits != null) out.maxHits = node.maxHits;
                break;
            default:
                break;
        }
        return out;
    }

    function deserialize(json) {
        const source = typeof json === "string" ? JSON.parse(json) : json;
        return fromObject(source);
    }

    function fromObject(source) {
        if (source == null) return null;
        const type = source.type;
        if (!["TreeNode", ...FLOW_TYPES, ...RULE_TYPES].includes(type)) {
            throw new Error(`不支持的节点类型: ${type}`);
        }
        const node = { type, name: source.name || "" };

        switch (type) {
            case "TreeNode":
                node.next = fromObject(source.next);
                break;
            case "ActionNode":
            case "ConditionNode":
            case "LoopNode":
                if (type === "LoopNode") node.max = source.max;
                node.rule = fromObject(source.rule);
                node.next = fromObject(source.next);
                break;
            case "SerialNode":
            case "ParallelNode":
                node.branches = (source.branches || []).map(fromObject);
                break;
            case "DecisionNode":
                node.branches = (source.branches || []).map(fromObject);
                node.defaultBranch = fromObject(source.defaultBranch);
                break;
            case "AtomicNode":
            case "BooleanNode":
                node.expr = source.expr;
                node.args = source.args || "";
                if (NEGATABLE.has(type)) node.negative = !!source.negative;
                break;
            case "LogicNode":
                node.expr = source.expr;
                node.negative = !!source.negative;
                node.rules = (source.rules || []).map(fromObject);
                break;
            case "MatchNode":
                node.expr = "some";
                node.negative = !!source.negative;
                node.rules = (source.rules || []).map(fromObject);
                node.minHits = source.minHits ?? null;
                node.maxHits = source.maxHits ?? null;
                break;
            default:
                break;
        }
        return node;
    }

    function toJson(node) {
        return JSON.stringify(serialize(node));
    }

    function rule(type, expr, name = "") {
        return { type, name, expr, args: "" };
    }

    const make = {
        root: () => ({ type: "TreeNode", name: "", next: null }),
        action: (expr) => ({
            type: "ActionNode", name: "",
            rule: expr == null || expr === "" ? null : rule("AtomicNode", expr), next: null
        }),
        condition: (conditionRule) => ({ type: "ConditionNode", name: "", rule: conditionRule || null, next: null }),
        serial: () => ({ type: "SerialNode", name: "", branches: [] }),
        parallel: () => ({ type: "ParallelNode", name: "", branches: [] }),
        decision: () => ({ type: "DecisionNode", name: "", branches: [], defaultBranch: null }),
        loop: (max, condition, action) => ({
            type: "LoopNode", name: "", max, rule: condition || null, next: action || null
        }),
        atom: (expr, name) => ({ ...rule("BooleanNode", expr, name || ""), negative: false }),
        logic: (expr) => ({ type: "LogicNode", name: "", expr: expr === "||" ? "||" : "&&", negative: false, rules: [] }),
        some: (minHits, maxHits) => ({
            type: "MatchNode", name: "", expr: "some", negative: false, rules: [],
            minHits: minHits ?? null, maxHits: maxHits ?? null
        })
    };

    return {
        FLOW_TYPES,
        childEdges,
        childNodes,
        executionEdges,
        hasArgs,
        visit,
        serialize,
        deserialize,
        toJson,
        make
    };
});
