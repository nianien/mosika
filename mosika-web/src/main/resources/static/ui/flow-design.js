(function (root, factory) {
    "use strict";
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    if (root) root.MosikaFlowDesign = api;
})(typeof self !== "undefined" ? self : (typeof globalThis !== "undefined" ? globalThis : this), function () {
    "use strict";

    const FLOW_TYPES = ["ActionNode", "ConditionNode", "SerialNode", "ParallelNode", "DecisionNode", "LoopNode"];
    const RULE_TYPES = ["BooleanNode", "LogicNode", "MatchNode"];
    const COMPOSITE_RULE_TYPES = ["LogicNode", "MatchNode"];
    const DEFAULT_BRANCH_TYPES = ["ActionNode", "SerialNode", "ParallelNode", "DecisionNode", "LoopNode"];

    const CAPABILITIES = {
        TreeNode: {
            config: "root",
            canvasLabel: "full",
            relations: {
                next: relation(FLOW_TYPES, "设置入口", 1, 1, "流程入口不能为空")
            }
        },
        ActionNode: {
            config: "action",
            canvasLabel: "full",
            passThrough: true,
            relations: {
                next: relation(FLOW_TYPES, "添加后续", 0, 1)
            }
        },
        ConditionNode: {
            config: "condition",
            canvasLabel: "full",
            passThrough: true,
            relations: {
                next: relation(FLOW_TYPES, "添加命中流程", 0, 1)
            }
        },
        SerialNode: {
            config: "structure",
            canvasLabel: "compact",
            conversions: ["SerialNode", "ParallelNode"],
            relations: {
                branch: relation(FLOW_TYPES, "添加步骤", 2, null, "串行节点至少需要两个步骤")
            }
        },
        ParallelNode: {
            config: "structure",
            canvasLabel: "compact",
            conversions: ["SerialNode", "ParallelNode"],
            relations: {
                branch: relation(FLOW_TYPES, "添加分支", 2, null, "并行节点至少需要两个分支")
            }
        },
        DecisionNode: {
            config: "structure",
            canvasLabel: "compact",
            relations: {
                decision: relation(["ConditionNode"], "添加条件分支", 1, null,
                    "分支节点至少需要一个条件分支和一个默认分支"),
                default: relation(DEFAULT_BRANCH_TYPES, "添加默认分支", 1, 1,
                    "分支节点至少需要一个条件分支和一个默认分支")
            }
        },
        LoopNode: {
            config: "loop",
            canvasLabel: "compact",
            passThrough: true,
            relations: {
                next: relation(FLOW_TYPES, "添加循环体", 1, 1, "循环体不能为空")
            }
        }
    };

    function relation(types, label, min, max, requiredMessage = "") {
        return { types, label, dialogTitle: label, min, max, requiredMessage };
    }

    function capability(nodeOrType) {
        const type = typeof nodeOrType === "string" ? nodeOrType : nodeOrType?.type;
        return CAPABILITIES[type] || null;
    }

    function relationCount(node, relationName) {
        if (relationName === "next") return node.next ? 1 : 0;
        if (relationName === "branch" || relationName === "decision") return node.branches?.length || 0;
        if (relationName === "default") return node.defaultBranch ? 1 : 0;
        return 0;
    }

    function relationEntries(node) {
        return Object.entries(capability(node)?.relations || {});
    }

    function availableRelations(node) {
        return relationEntries(node)
            .filter(([name, definition]) => definition.max == null || relationCount(node, name) < definition.max)
            .map(([name]) => name);
    }

    function operations(node) {
        const available = new Set(availableRelations(node));
        return relationEntries(node)
            .filter(([name]) => available.has(name))
            .map(([name, definition]) => ({
                relation: name,
                label: definition.label,
                dialogTitle: definition.dialogTitle
            }));
    }

    function requiredRelations(node) {
        return relationEntries(node).flatMap(([name, definition]) =>
            Array(Math.max(0, definition.min - relationCount(node, name))).fill(name));
    }

    function selfIssues(node) {
        const config = capability(node)?.config;
        if (config === "action") {
            return (node.rule?.expr || "").trim() ? [] : ["请选择动作规则"];
        }
        if (config === "condition") {
            return conditionIssues(node.rule, "请配置判断条件");
        }
        if (config === "loop") {
            const issues = [];
            if (!Number.isInteger(node.max) || node.max < 0) issues.push("最大循环次数必须是非负整数");
            issues.push(...conditionIssues(node.rule, "循环条件不能为空"));
            return issues;
        }
        return [];
    }

    function conditionIssues(rule, message) {
        return rule && (rule.type !== "BooleanNode" || (rule.expr || "").trim()) ? [] : [message];
    }

    function validationIssues(node) {
        const issues = selfIssues(node);
        requiredRelations(node).forEach((name) => {
            const message = capability(node)?.relations?.[name]?.requiredMessage;
            if (message && !issues.includes(message)) issues.push(message);
        });
        return issues;
    }

    function relationTypes(node, relationName) {
        return capability(node)?.relations?.[relationName]?.types || [];
    }

    function relationAllows(node, relationName, childType) {
        return relationTypes(node, relationName).includes(childType);
    }

    function isConditionOwner(nodeOrType) {
        const config = capability(nodeOrType)?.config;
        return config === "condition" || config === "loop";
    }

    function isLocallyConfigurable(nodeOrType) {
        return ["action", "condition", "loop"].includes(capability(nodeOrType)?.config);
    }

    function isPassThrough(nodeOrType) {
        return capability(nodeOrType)?.passThrough === true;
    }

    function conversionTypes(nodeOrType) {
        return capability(nodeOrType)?.conversions || [];
    }

    function usesCompactCanvasLabel(nodeOrType) {
        return capability(nodeOrType)?.canvasLabel === "compact";
    }

    function isCompositeRule(nodeOrType) {
        const type = typeof nodeOrType === "string" ? nodeOrType : nodeOrType?.type;
        return COMPOSITE_RULE_TYPES.includes(type);
    }

    function canAddRule(nodeOrType) {
        const type = typeof nodeOrType === "string" ? nodeOrType : nodeOrType?.type;
        return RULE_TYPES.includes(type);
    }

    return {
        FLOW_TYPES,
        RULE_TYPES,
        COMPOSITE_RULE_TYPES,
        CAPABILITIES,
        capability,
        availableRelations,
        operations,
        requiredRelations,
        selfIssues,
        validationIssues,
        relationTypes,
        relationAllows,
        isConditionOwner,
        isLocallyConfigurable,
        isPassThrough,
        conversionTypes,
        usesCompactCanvasLabel,
        isCompositeRule,
        canAddRule
    };
});
