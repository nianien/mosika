(function (root, factory) {
    "use strict";
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    if (root) root.MosikaTree = api;
})(typeof self !== "undefined" ? self : (typeof globalThis !== "undefined" ? globalThis : this), function () {
    "use strict";

    const FLOW_TYPES = ["A", "C", "S", "P", "D"];
    const RULE_TYPES = new Set(["R", "B", "L", "H"]);
    const NEGATABLE = new Set(["B", "L", "H"]);
    const ARGS = new Set(["R", "B"]);

    function edge(relation, node, kind, owner, field, list, index) {
        return { relation, node, kind, owner, field, list, index };
    }

    function childEdges(node) {
        if (!node) return [];
        const edges = [];
        switch (node.type) {
            case "T":
                if (node.next) edges.push(edge("next", node.next, "single", node, "next"));
                break;
            case "A":
                if (node.rule) edges.push(edge("rule", node.rule, "single", node, "rule"));
                if (node.next) edges.push(edge("next", node.next, "single", node, "next"));
                break;
            case "C":
                if (node.rule) edges.push(edge("rule", node.rule, "single", node, "rule"));
                if (node.next) edges.push(edge("matched", node.next, "single", node, "next"));
                break;
            case "S":
            case "P":
                (node.branches || []).forEach((child, index) => {
                    if (child) edges.push(edge("branch", child, "list", node, "branches", node.branches, index));
                });
                break;
            case "D":
                (node.branches || []).forEach((child, index) => {
                    if (child) edges.push(edge("decision", child, "list", node, "branches", node.branches, index));
                });
                if (node.defaultBranch) edges.push(edge("default", node.defaultBranch, "single", node, "defaultBranch"));
                break;
            case "L":
            case "H":
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
        if (type === "PH") throw new Error("存在待配置的占位节点，无法保存");
        if (!["T", ...FLOW_TYPES, ...RULE_TYPES].includes(type)) {
            throw new Error(`不支持的节点类型: ${type}`);
        }
        const out = { type, name: node.name || "" };

        switch (type) {
            case "T":
                if (node.next) out.next = serialize(node.next);
                break;
            case "A":
            case "C":
                if (node.rule) out.rule = serialize(node.rule);
                if (node.next) out.next = serialize(node.next);
                break;
            case "S":
            case "P":
                out.branches = (node.branches || []).map(serialize);
                break;
            case "D":
                out.branches = (node.branches || []).map(serialize);
                if (node.defaultBranch) out.defaultBranch = serialize(node.defaultBranch);
                break;
            case "R":
            case "B":
                out.expr = node.expr;
                if (ARGS.has(type) && node.args != null && node.args !== "") out.args = node.args;
                if (NEGATABLE.has(type)) out.negative = !!node.negative;
                break;
            case "L":
                out.expr = node.expr;
                out.negative = !!node.negative;
                out.rules = (node.rules || []).map(serialize);
                break;
            case "H":
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
        if (!["T", ...FLOW_TYPES, ...RULE_TYPES, "PH"].includes(type)) {
            throw new Error(`不支持的节点类型: ${type}`);
        }
        if (type === "PH") return { type, slot: source.slot || "branch" };
        const node = { type, name: source.name || "" };

        switch (type) {
            case "T":
                node.next = fromObject(source.next);
                break;
            case "A":
            case "C":
                node.rule = fromObject(source.rule);
                node.next = fromObject(source.next);
                break;
            case "S":
            case "P":
                node.branches = (source.branches || []).map(fromObject);
                break;
            case "D":
                node.branches = (source.branches || []).map(fromObject);
                node.defaultBranch = fromObject(source.defaultBranch);
                break;
            case "R":
            case "B":
                node.expr = source.expr;
                node.args = source.args || "";
                if (NEGATABLE.has(type)) node.negative = !!source.negative;
                break;
            case "L":
                node.expr = source.expr;
                node.negative = !!source.negative;
                node.rules = (source.rules || []).map(fromObject);
                break;
            case "H":
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
        root: () => ({ type: "T", name: "", next: null }),
        action: (expr) => ({ type: "A", name: "", rule: rule("R", expr), next: null }),
        condition: (conditionRule) => ({ type: "C", name: "", rule: conditionRule || null, next: null }),
        serial: () => ({ type: "S", name: "", branches: [] }),
        parallel: () => ({ type: "P", name: "", branches: [] }),
        decision: () => ({ type: "D", name: "", branches: [], defaultBranch: null }),
        atom: (expr, name) => ({ ...rule("B", expr, name || ""), negative: false }),
        logic: (expr) => ({ type: "L", name: "", expr: expr === "||" ? "||" : "&&", negative: false, rules: [] }),
        some: (minHits, maxHits) => ({
            type: "H", name: "", expr: "some", negative: false, rules: [],
            minHits: minHits ?? null, maxHits: maxHits ?? null
        }),
        placeholder: (slot) => ({ type: "PH", slot: slot || "branch" })
    };

    return {
        FLOW_TYPES,
        childEdges,
        childNodes,
        executionEdges,
        visit,
        serialize,
        deserialize,
        toJson,
        make
    };
});
