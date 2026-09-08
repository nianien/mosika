"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const Design = require("../../main/resources/static/ui/flow-design.js");
const Tree = require("../../main/resources/static/ui/treenode.js");

const action = () => ({ type: "ActionNode", rule: { type: "AtomicNode", expr: "r1" }, next: null });
const condition = () => ({ type: "ConditionNode", rule: { type: "BooleanNode", expr: "r2" }, next: null });

test("single branch nodes share pass-through behavior", () => {
    assert.equal(Design.isPassThrough("ActionNode"), true);
    assert.equal(Design.isPassThrough("ConditionNode"), true);
    assert.equal(Design.isPassThrough("LoopNode"), true);
    assert.equal(Design.isPassThrough("SerialNode"), false);
});

test("available relations come from node capabilities", () => {
    const node = action();
    assert.deepEqual(Design.availableRelations(node), ["next"]);
    node.next = condition();
    assert.deepEqual(Design.availableRelations(node), []);

    assert.deepEqual(Design.availableRelations({ type: "DecisionNode", branches: [], defaultBranch: null }),
        ["decision", "default"]);
});

test("required relations drive recursive slots", () => {
    assert.deepEqual(Design.requiredRelations({ type: "TreeNode", next: null }), ["next"]);
    assert.deepEqual(Design.requiredRelations({ type: "LoopNode", max: 1, rule: {}, next: null }), ["next"]);
    assert.deepEqual(Design.requiredRelations({ type: "SerialNode", branches: [] }), ["branch", "branch"]);
    assert.deepEqual(Design.requiredRelations({ type: "SerialNode", branches: [action()] }), ["branch"]);
    assert.deepEqual(Design.requiredRelations({ type: "ParallelNode", branches: [action(), action()] }), []);
    assert.deepEqual(Design.requiredRelations({ type: "DecisionNode", branches: [], defaultBranch: null }),
        ["decision", "default"]);
    assert.deepEqual(Design.requiredRelations({
        type: "DecisionNode", branches: [condition()], defaultBranch: action()
    }), []);
});

test("validation uses the same required relations", () => {
    assert.deepEqual(Design.validationIssues({ type: "TreeNode", next: null }), ["流程入口不能为空"]);
    assert.deepEqual(Design.validationIssues({ type: "SerialNode", branches: [] }), ["串行节点至少需要两个步骤"]);
    assert.deepEqual(Design.validationIssues({ type: "DecisionNode", branches: [], defaultBranch: null }),
        ["分支节点至少需要一个条件分支和一个默认分支"]);
    assert.deepEqual(Design.validationIssues({ type: "LoopNode", max: -1, rule: null, next: null }), [
        "最大循环次数必须是非负整数",
        "循环条件不能为空",
        "循环体不能为空"
    ]);
    assert.deepEqual(Design.validationIssues({
        type: "LoopNode", max: 3, rule: { type: "BooleanNode", expr: "" }, next: action()
    }), ["循环条件不能为空"]);
});

test("every flow type has one centralized capability declaration", () => {
    assert.deepEqual(Design.FLOW_TYPES, Tree.FLOW_TYPES);
    Design.FLOW_TYPES.forEach((type) => {
        const definition = Design.capability(type);
        assert.ok(definition, `${type} capability is missing`);
        assert.ok(definition.config, `${type} config is missing`);
        assert.ok(["full", "compact"].includes(definition.canvasLabel), `${type} canvas label policy is missing`);
        assert.ok(definition.relations, `${type} relations are missing`);
    });
});

test("tree model owns the rule argument capability", () => {
    assert.equal(Tree.hasArgs("AtomicNode"), true);
    assert.equal(Tree.hasArgs("BooleanNode"), true);
    assert.equal(Tree.hasArgs("LogicNode"), false);
    assert.equal(Tree.hasArgs("MatchNode"), false);
});

test("decision relations preserve branch constraints", () => {
    const decision = { type: "DecisionNode", branches: [], defaultBranch: null };
    assert.equal(Design.relationAllows(decision, "decision", "ConditionNode"), true);
    assert.equal(Design.relationAllows(decision, "decision", "ActionNode"), false);
    assert.equal(Design.relationAllows(decision, "default", "ConditionNode"), false);
    assert.equal(Design.relationAllows(decision, "default", "LoopNode"), true);
});

test("only serial and parallel nodes are mutually convertible", () => {
    assert.deepEqual(Design.conversionTypes("SerialNode"), ["SerialNode", "ParallelNode"]);
    assert.deepEqual(Design.conversionTypes("ParallelNode"), ["SerialNode", "ParallelNode"]);
    assert.deepEqual(Design.conversionTypes("LoopNode"), []);
});

test("canvas label policy keeps structural controls compact", () => {
    assert.equal(Design.usesCompactCanvasLabel("SerialNode"), true);
    assert.equal(Design.usesCompactCanvasLabel("ParallelNode"), true);
    assert.equal(Design.usesCompactCanvasLabel("DecisionNode"), true);
    assert.equal(Design.usesCompactCanvasLabel("LoopNode"), true);
    assert.equal(Design.usesCompactCanvasLabel("ConditionNode"), false);
    assert.equal(Design.usesCompactCanvasLabel("ActionNode"), false);
});

test("rule editing capabilities cover boolean and both composite rules", () => {
    assert.equal(Design.canAddRule("BooleanNode"), true);
    assert.equal(Design.canAddRule("LogicNode"), true);
    assert.equal(Design.canAddRule("MatchNode"), true);
    assert.equal(Design.isCompositeRule("BooleanNode"), false);
    assert.equal(Design.isCompositeRule("LogicNode"), true);
    assert.equal(Design.isCompositeRule("MatchNode"), true);
});
