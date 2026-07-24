(() => {
    "use strict";

    const TYPES = {
        ROOT: { name: "根节点", kind: "root", short: "开始", help: "规则树入口，仅承担根节点定位。" },
        S: { name: "串行节点", kind: "structure", short: "串", help: "子节点按顺序执行，顺序由树中从左到右的位置表达。" },
        P: { name: "并行节点", kind: "structure", short: "并", help: "多个子节点并发执行；结构节点本身不承载业务结果。" },
        D: { name: "分支节点", kind: "structure", short: "分", help: "按顺序检查各条件，多选一并在首个命中后停止，最后可设置默认分支。" },
        J: { name: "条件节点", kind: "structure", short: "条件", help: "引用一棵可递归嵌套的纯规则树，并连接可选的后续流程。" },
        L: { name: "逻辑", kind: "structure", short: "与", help: "使用“与”或“或”组合两个及以上纯规则子节点。" },
        H: { name: "命中数", kind: "structure", short: "H", help: "表达 hits(min,max,...)；例如至少命中 2 项。" },
        R: { name: "原子规则", kind: "condition", short: "R", help: "只参与规则匹配的原子表达式，不连接业务动作。" },
        C: { name: "条件节点", kind: "condition", short: "条件", help: "引用一条后台条件规则，可连接一个可选的后续流程。" },
        A: { name: "动作节点", kind: "action", short: "动作", help: "引用一条后台动作规则，执行后可连接一个可选的下一步。" }
    };

    // 模拟 RuleDefinition 查询结果。expression 属于后端定义，不下发给本编辑器；
    // RNode.expression 只保存稳定的 ruleId 引用，name 是节点自身可选的语义别名。
    const RULE_DEFINITIONS = Array.from({ length: 12 }, (_, index) => ({
        ruleId: `c${index + 1}`,
        desc: `业务判断规则${index + 1}`,
        useType: 0
    }));
    const ACTION_DEFINITIONS = Array.from({ length: 13 }, (_, index) => ({
        ruleId: `a${index + 1}`,
        desc: `业务操作${index + 1}`,
        useType: 0
    }));

    // 与 src/main/resources/img/ui-tree.png 逐节点对应。
    const sampleTree = () => ({
        id: "n1", type: "ROOT", relation: "root", children: [
            { id: "n2", type: "S", relation: "root", children: [
                { id: "n3", type: "P", relation: "branch", children: [
                    { id: "n4", type: "A", label: "业务操作11", expression: "a11", relation: "branch", children: [] },
                    { id: "n5", type: "A", label: "业务操作12", expression: "a12", relation: "branch", children: [] },
                    { id: "n6", type: "A", label: "业务操作13", expression: "a13", relation: "branch", children: [] }
                ]},
                { id: "n7", type: "D", relation: "branch", children: [
                    { id: "n8", type: "J", relation: "decision", children: [
                        { id: "n9", type: "L", name: "业务复合规则1", expression: "||", relation: "rule", children: [
                            { id: "n10", type: "R", name: "", expression: "c1", relation: "rule", children: [] },
                            { id: "n11", type: "L", name: "", expression: "&&", relation: "rule", children: [
                                { id: "n12", type: "R", name: "", expression: "c2", relation: "rule", children: [] },
                                { id: "n13", type: "R", name: "", expression: "c3", relation: "rule", children: [] }
                            ]}
                        ]},
                        { id: "n14", type: "S", relation: "action", children: [
                            { id: "n15", type: "D", relation: "branch", children: [
                            { id: "n16", type: "J", relation: "decision", children: [
                                { id: "n37", type: "R", name: "", expression: "c1", relation: "rule", children: [] },
                                { id: "n17", type: "A", label: "业务操作1", expression: "a1", relation: "action", children: [] }
                            ]},
                                { id: "n18", type: "J", relation: "decision", children: [
                                    { id: "n19", type: "L", name: "业务复合规则2", expression: "&&", relation: "rule", children: [
                                        { id: "n20", type: "R", name: "", expression: "c2", relation: "rule", children: [] },
                                        { id: "n21", type: "R", name: "", expression: "c3", relation: "rule", children: [] }
                                    ]},
                                    { id: "n22", type: "A", label: "业务操作3", expression: "a3", relation: "action", children: [] }
                                ]}
                            ]},
                            { id: "n23", type: "J", relation: "branch", children: [
                                { id: "n38", type: "R", name: "", expression: "c4", relation: "rule", children: [] },
                                { id: "n24", type: "A", label: "业务操作4", expression: "a4", relation: "action", children: [] }
                            ]}
                        ]}
                    ]},
                    { id: "n25", type: "J", relation: "decision", children: [
                        { id: "n39", type: "R", name: "", expression: "c5", relation: "rule", children: [] },
                        { id: "n26", type: "D", relation: "action", children: [
                            { id: "n27", type: "J", relation: "decision", children: [
                                { id: "n40", type: "R", name: "", expression: "c6", relation: "rule", children: [] },
                                { id: "n28", type: "A", label: "业务操作6", expression: "a6", relation: "action", children: [] }
                            ]},
                            { id: "n29", type: "J", relation: "decision", children: [
                                { id: "n41", type: "R", name: "", expression: "c7", relation: "rule", children: [] },
                                { id: "n30", type: "A", label: "业务操作7", expression: "a7", relation: "action", children: [] }
                            ]},
                            { id: "n31", type: "A", label: "业务操作5", expression: "a5", relation: "default", children: [] }
                        ]}
                    ]},
                    { id: "n32", type: "S", relation: "default", children: [
                        { id: "n33", type: "J", relation: "branch", children: [
                            { id: "n42", type: "R", name: "", expression: "c8", relation: "rule", children: [] },
                            { id: "n34", type: "A", label: "业务操作8", expression: "a8", relation: "action", children: [] }
                        ]},
                        { id: "n35", type: "J", relation: "branch", children: [
                            { id: "n43", type: "R", name: "", expression: "c9", relation: "rule", children: [] },
                            { id: "n36", type: "A", label: "业务操作9", expression: "a9", relation: "action", children: [] }
                        ]}
                    ]}
                ]}
            ]}
        ]
    });

    let tree = sampleTree();
    let selectedId = null;
    let nextId = 44;
    let addRelation = null;
    let scale = 1;
    let panX = 0;
    let panY = 0;
    let fitMode = true;
    let drag = null;
    let ruleJudgeId = null;
    let ruleSelectedId = null;
    let ruleAddMode = "child";
    let ruleAddParentId = null;
    let ruleAddAnchorId = null;
    let ruleAddRuleIds = [];
    let ruleExtendRuleIds = [];
    let rulePopoverAnchorId = null;
    let rulePopoverPoint = null;
    let ruleEditorEditing = false;
    let ruleEditorDraftNodeId = null;
    let closeRuleDialogAfterEdit = false;
    let deleteNodeId = null;

    const FLOW_TYPES = ["A", "S", "P", "D", "J"];
    const RELATIONS = {
        root: { types: FLOW_TYPES },
        next: { types: FLOW_TYPES },
        action: { types: FLOW_TYPES },
        branch: { types: FLOW_TYPES },
        decision: { types: ["J"] },
        default: { types: FLOW_TYPES }
    };

    const $ = (selector) => document.querySelector(selector);
    const treeRoot = $("#treeRoot");
    const viewport = $("#treeViewport");
    const stage = $("#treeStage");
    const dialog = $("#nodeDialog");
    const nodeEditDialog = $("#nodeEditDialog");
    const deleteNodeDialog = $("#deleteNodeDialog");
    const ruleDialog = $("#ruleDialog");
    const ruleTreeRoot = $("#ruleTreeRoot");

    function walk(node, visitor, parent = null) {
        if (visitor(node, parent) === false) return false;
        for (const child of node.children) {
            if (walk(child, visitor, node) === false) return false;
        }
        return true;
    }

    function findNode(id) {
        let found = null;
        walk(tree, (node, parent) => {
            if (node.id === id) {
                found = { node, parent };
                return false;
            }
            return true;
        });
        return found;
    }

    function countNodes(node) {
        return 1 + node.children.reduce((sum, child) => sum + countNodes(child), 0);
    }

    function countFlowNodes(node) {
        if (node.type === "J") {
            return 1 + judgeParts(node).flows.reduce((sum, child) => sum + countFlowNodes(child), 0);
        }
        return 1 + node.children.reduce((sum, child) => sum + countFlowNodes(child), 0);
    }

    function judgeParts(node) {
        return {
            rule: node.children.find((child) => child.relation === "rule") || null,
            flows: node.children.filter((child) => child.relation !== "rule")
        };
    }

    function ruleDisplayName(node) {
        const { rule } = judgeParts(node);
        if (!rule) return "未配置规则";
        return ruleNodeDisplayName(rule);
    }

    function flowNodeName(node) {
        if (node.type === "J") {
            return judgeParts(node).rule?.name?.trim() || "";
        }
        return node.name?.trim() || "";
    }

    function ruleNodeDisplayName(node) {
        const alias = node.name?.trim();
        if (alias) return alias;
        if (node.type === "R") {
            return ruleDefinitionById(node.expression)?.desc || "未命名规则";
        }
        return ["L", "H"].includes(node.type) ? "复合规则" : TYPES[node.type]?.name || "未命名规则";
    }

    function isStructuralFlowType(type) {
        return ["ROOT", "S", "P", "D"].includes(type);
    }

    function flowNodeDisplayName(node) {
        if (node.type === "ROOT") return "开始";
        const name = flowNodeName(node);
        if (name) return name;
        if (isStructuralFlowType(node.type)) return TYPES[node.type].name;
        if (node.type === "J") return ruleDisplayName(node);
        if (node.type === "A") {
            return actionDefinitionById(node.expression)?.desc
                || node.label?.trim()
                || TYPES[node.type].name;
        }
        if (node.type === "C") {
            return ruleDefinitionById(node.expression)?.desc
                || node.label?.trim()
                || TYPES[node.type].name;
        }
        return node.label?.trim() || TYPES[node.type]?.name || "未命名节点";
    }

    function countHiddenNodes(node) {
        if (node.type === "J") {
            return judgeParts(node).flows.reduce((sum, child) => sum + countHiddenNodes(child), 0);
        }
        if (node.collapsed && node.children.length) return countFlowNodes(node) - 1;
        return node.children.reduce((sum, child) => sum + countHiddenNodes(child), 0);
    }

    function countModalRuleNodes(node) {
        if (node.type === "J") {
            const { rule, flows } = judgeParts(node);
            return (rule ? countNodes(rule) : 0)
                + flows.reduce((sum, child) => sum + countModalRuleNodes(child), 0);
        }
        return node.children.reduce((sum, child) => sum + countModalRuleNodes(child), 0);
    }

    function availableRelations(parent) {
        const has = (relation) => parent.children.some((child) => child.relation === relation);
        if (parent.type === "ROOT") return has("root") ? [] : ["root"];
        if (parent.type === "A") return has("next") ? [] : ["next"];
        if (parent.type === "C") return has("action") ? [] : ["action"];
        if (parent.type === "D") return has("default") ? ["decision"] : ["decision", "default"];
        if (parent.type === "J") return [!has("action") && "action"].filter(Boolean);
        if (["S", "P"].includes(parent.type)) return ["branch"];
        return [];
    }

    function flowNodeOperations(node) {
        const available = new Set(availableRelations(node));
        const operations = [];
        const add = (relation, label, dialogTitle) => {
            if (available.has(relation)) operations.push({ relation, label, dialogTitle });
        };
        if (node.type === "ROOT") {
            add("root", "设置流程入口", "设置流程入口");
        } else if (node.type === "A") {
            add("next", "添加下一步", "添加下一步");
        } else if (["C", "J"].includes(node.type)) {
            add("action", "添加后续", "添加后续");
        } else if (node.type === "S") {
            add("branch", "添加分支", "添加分支");
        } else if (node.type === "P") {
            add("branch", "添加分支", "添加分支");
        } else if (node.type === "D") {
            add("decision", "添加分支", "添加分支");
            add("default", "设置默认分支", "设置默认分支");
        }
        return operations;
    }

    function relationChild(node, relation) {
        return node.children.find((child) => child.relation === relation) || null;
    }

    function canDeleteFlowNode(node, parent) {
        if (!parent) return false;
        if (["S", "P"].includes(parent.type) && node.relation === "branch") {
            return parent.children.filter((child) => child.relation === "branch").length > 1;
        }
        if (parent.type === "D" && node.relation === "decision") {
            return parent.children.filter((child) => child.relation === "decision").length > 1;
        }
        return true;
    }

    function escapeText(value) {
        return String(value ?? "").replace(/[&<>'"]/g, (char) => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
        })[char]);
    }

    function compactStructureLabel(node, type) {
        if (node.type === "L") return node.expression === "||" ? "或" : "与";
        if (node.type === "S") return "串";
        if (node.type === "P") return "并";
        if (node.type === "D") return "分";
        if (node.type === "J") return "规则";
        if (node.type !== "H") return type.short;
        const bounds = String(node.expression || "").match(/^hits\(([^,]+),([^,]+),/);
        if (!bounds) return "H";
        const low = bounds[1].trim();
        const high = bounds[2].trim();
        if (high === "_") return `${low}+`;
        if (low === "_") return `≤${high}`;
        if (low === high) return `=${low}`;
        return `${low}…${high}`;
    }

    function renderBranch(node) {
        const type = TYPES[node.type] || TYPES.A;
        const expression = node.expression && type.kind !== "structure" && type.kind !== "root"
            ? `<span class="node-expression">${escapeText(node.expression)}</span>` : "";
        const { flows } = node.type === "J" ? judgeParts(node) : { flows: [] };
        const collapsed = Boolean(node.type !== "J" && node.collapsed && node.children.length);
        const visibleChildren = node.type === "J" ? flows : (collapsed ? [] : node.children);
        const children = visibleChildren.length
            ? `<div class="tree-children">${visibleChildren.map((child) => renderBranch(child)).join("")}</div>` : "";
        const collapsedBadge = collapsed ? `<span class="collapsed-count">+${countFlowNodes(node) - 1}</span>` : "";
        const title = node.type === "J"
            ? ruleDisplayName(node)
            : (type.kind === "structure" || type.kind === "root"
                ? compactStructureLabel(node, type)
                : flowNodeDisplayName(node));
        const cardKind = `${node.type === "J" ? "condition judge-summary" : type.kind} node-type-${node.type.toLowerCase()}`;
        const cardTitle = node.type === "J"
            ? `条件节点 · ${ruleDisplayName(node)}`
            : (["A", "C"].includes(node.type)
                ? `${type.name} · ${flowNodeDisplayName(node)}`
                : type.name);
        const canCollapse = node.type !== "J" && Boolean(node.children.length);
        const nodeShell = `
            <div class="node-shell${canCollapse ? " has-fold-toggle" : ""}" data-node-id="${node.id}" tabindex="0" role="treeitem" aria-selected="${selectedId === node.id}" aria-expanded="${!collapsed}">
                    <div class="node-card ${cardKind}" title="${escapeText(cardTitle)}">
                        <span class="node-title">${escapeText(title || type.short)}</span>
                        ${expression}
                    </div>
                    ${canCollapse ? `<button class="node-fold-toggle" type="button" data-action="collapse" title="${collapsed ? "展开分支" : "折叠分支"}" aria-label="${collapsed ? "展开" : "收起"}">${collapsed ? "+" : "−"}</button>` : ""}
                    ${collapsedBadge}
                </div>`;

        return `
            <div class="tree-branch" data-branch-id="${node.id}">
                ${nodeShell}
                ${children}
            </div>`;
    }

    function render({ preserveView = true } = {}) {
        treeRoot.innerHTML = renderBranch(tree);
        const hiddenNodes = countHiddenNodes(tree);
        const modalRuleNodes = countModalRuleNodes(tree);
        $("#nodeCount").textContent = `${countFlowNodes(tree)} 个流程节点${modalRuleNodes ? ` · ${modalRuleNodes} 个规则节点在弹窗` : ""}${hiddenNodes ? ` · ${hiddenNodes} 个流程已折叠` : ""}`;
        updateInspector();
        requestAnimationFrame(() => {
            if (!preserveView || fitMode) fitTree();
            else applyTransform();
        });
    }

    function populateTypeSelect(select, allowedTypes) {
        select.innerHTML = allowedTypes
            .map((key) => `<option value="${key}">${TYPES[key].name}</option>`).join("");
    }

    function definitionReference(definitions, ruleId, fallback = "") {
        const definition = definitions.find((candidate) => candidate.ruleId === ruleId);
        if (definition) return `${definition.desc} · ${definition.ruleId}`;
        if (!ruleId) return "未设置";
        return fallback && fallback !== ruleId ? `${fallback} · ${ruleId}` : ruleId;
    }

    function judgeRuleReference(node) {
        const rule = judgeParts(node).rule;
        if (!rule) return "未设置";
        if (rule.type === "R") {
            return definitionReference(RULE_DEFINITIONS, rule.expression);
        }
        return `复合规则 · ${countNodes(rule)} 个规则节点`;
    }

    function updateInspector() {
        const found = findNode(selectedId);
        const empty = $("#inspectorEmpty");
        const form = $("#inspectorForm");
        if (!found) {
            empty.hidden = false;
            form.hidden = true;
            return;
        }
        const { node } = found;
        empty.hidden = true;
        form.hidden = false;
        $("#inspectorTitle").textContent = "节点属性";
        $("#nodeType").textContent = TYPES[node.type].name;
        const nodeName = flowNodeName(node);
        const fields = nodeName ? [{ label: "节点名称", value: nodeName }] : [];
        if (node.type === "J") {
            fields.push({ label: "引用规则", value: judgeRuleReference(node) });
        } else if (node.type === "A") {
            const next = relationChild(node, "next");
            fields.push({
                label: "引用规则",
                value: definitionReference(ACTION_DEFINITIONS, node.expression, node.label)
            });
            if (next) {
                fields.push({ label: "下一步", value: flowNodeDisplayName(next) });
            }
        } else if (node.type === "C") {
            fields.push({
                label: "引用规则",
                value: definitionReference(RULE_DEFINITIONS, node.expression, node.label)
            });
        } else if (["S", "P"].includes(node.type)) {
            fields.push({ label: "分支数", value: `${node.children.length} 个` });
        } else if (node.type === "D") {
            const branches = node.children.filter((child) => child.relation === "decision").length;
            fields.push({ label: "分支数", value: `${branches} 个` });
            fields.push({
                label: "默认分支",
                value: relationChild(node, "default") ? "已设置" : "未设置"
            });
        } else {
            const root = relationChild(node, "root");
            fields.push({
                label: "流程入口",
                value: root ? flowNodeDisplayName(root) : "未设置"
            });
        }
        $("#nodeDetailFields").innerHTML = fields.map((field) => `
            <label>
                ${escapeText(field.label)}
                <input value="${escapeText(field.value)}" readonly>
            </label>
        `).join("");
        const operations = flowNodeOperations(node);
        const primaryOperation = ["ROOT", "S", "P", "D"].includes(node.type);
        $("#flowNodeActions").innerHTML = operations.map((operation, index) => `
            <button class="${primaryOperation && index === 0 ? "primary " : ""}flow-operation${operations.length === 1 ? " wide" : ""}"
                    type="button" data-flow-relation="${operation.relation}">
                ${escapeText(operation.label)}
            </button>
        `).join("");
        const editButton = $("#editNodeButton");
        editButton.textContent = node.type === "J" ? "查看规则" : "编辑";
        editButton.hidden = node.type === "ROOT";
        editButton.classList.toggle("primary", ["A", "C", "J"].includes(node.type));
        editButton.style.order = ["A", "C", "J"].includes(node.type) ? "-1" : "0";
        $(".inspector-actions").hidden = operations.length === 0 && editButton.hidden;
        $("#deleteButton").hidden = node.type === "ROOT";
        $("#deleteButton").disabled = !canDeleteFlowNode(node, found.parent);
        $("#deleteButton").title = $("#deleteButton").disabled
            ? "当前节点是所属结构的唯一必需分支，不能直接删除"
            : "";
    }

    function selectNode(id) {
        selectedId = id;
        treeRoot.querySelectorAll(".node-shell").forEach((shell) => {
            shell.setAttribute("aria-selected", String(shell.dataset.nodeId === id));
        });
        updateInspector();
    }

    function openSelectedNodeEditor() {
        const found = findNode(selectedId);
        if (!found) return;
        const { node } = found;
        if (node.type === "ROOT") return;
        if (node.type === "J") {
            openRuleDialog(node.id);
            return;
        }
        const ruleBacked = ["A", "C"].includes(node.type);
        $("#nodeEditDialogTitle").textContent = "编辑节点";
        $("#editNodeDefinitionField").hidden = !ruleBacked;
        $("#editNodeDefinition").disabled = !ruleBacked;
        if (ruleBacked) {
            populateFlowDefinitionSelect(
                $("#editNodeDefinition"),
                node.type === "A" ? ACTION_DEFINITIONS : RULE_DEFINITIONS,
                node.expression,
                node.label
            );
        }
        $("#editNodeAlias").value = flowNodeName(node);
        $("#editNodeAlias").placeholder = ruleBacked ? flowNodeDisplayName(node) : "";
        nodeEditDialog.showModal();
        requestAnimationFrame(() => {
            (ruleBacked ? $("#editNodeDefinition") : $("#editNodeAlias")).focus();
        });
    }

    function saveSelectedNodeEditor() {
        const found = findNode(selectedId);
        if (!found || !["A", "C", "S", "P", "D"].includes(found.node.type)) return;
        if (["A", "C"].includes(found.node.type)) {
            const definitions = found.node.type === "A" ? ACTION_DEFINITIONS : RULE_DEFINITIONS;
            const selectedRuleId = $("#editNodeDefinition").value;
            const definition = definitions.find((candidate) =>
                candidate.ruleId === selectedRuleId);
            if (!definition && selectedRuleId !== found.node.expression) return;
            if (definition) {
                found.node.label = definition.desc;
                found.node.expression = definition.ruleId;
            }
        }
        found.node.name = $("#editNodeAlias").value.trim();
        render({ preserveView: true });
    }

    function currentRuleJudge() {
        const found = findNode(ruleJudgeId);
        return found?.node.type === "J" ? found.node : null;
    }

    function findRuleNode(id) {
        const judge = currentRuleJudge();
        const root = judge ? judgeParts(judge).rule : null;
        if (!root || !id) return null;
        let found = null;
        walk(root, (node, parent) => {
            if (node.id === id) {
                found = { node, parent: parent || judge };
                return false;
            }
            return true;
        });
        return found;
    }

    function renderRuleBranch(node, parent = null) {
        const type = TYPES[node.type] || TYPES.R;
        const expression = node.expression
            ? `<span class="node-expression">${escapeText(node.expression)}</span>` : "";
        const children = node.children.length
            ? `<div class="rule-children">${node.children.map((child) => renderRuleBranch(child, node)).join("")}</div>` : "";
        const title = type.kind === "structure" ? compactStructureLabel(node, type) : ruleNodeDisplayName(node);
        return `
            <div class="rule-branch" data-rule-branch-id="${node.id}">
                <div class="node-shell" data-rule-node-id="${node.id}" tabindex="0" role="treeitem" aria-selected="${ruleSelectedId === node.id}">
                    <div class="node-card ${type.kind} node-type-${node.type.toLowerCase()}" aria-label="${escapeText(`${type.name} · ${ruleNodeDisplayName(node)}`)}">
                        <span class="node-title">${escapeText(title || type.short)}</span>
                        ${expression}
                    </div>
                </div>
                ${children}
            </div>`;
    }

    function canDeleteRule(found) {
        if (!found) return false;
        if (found.parent?.type === "L" && found.parent.children.length <= 2) return false;
        if (found.parent?.type === "H" && found.parent.children.length <= 1) return false;
        return true;
    }

    function renderRuleTree() {
        const judge = currentRuleJudge();
        const rule = judge ? judgeParts(judge).rule : null;
        if (!judge) return;
        if (ruleSelectedId && !findRuleNode(ruleSelectedId)) ruleSelectedId = rule.id;
        ruleTreeRoot.innerHTML = rule
            ? renderRuleBranch(rule)
            : `<div class="rule-tree-empty">
                <span>尚无规则子树</span>
                <span>从这里配置第一条规则</span>
                <button class="primary" id="ruleAddRootButton" type="button">＋ 配置根规则</button>
            </div>`;
        $("#ruleNodeCount").textContent = rule ? `${countNodes(rule)} 个规则节点` : "0 个规则节点";
    }

    function updateRuleEditor() {
        const found = findRuleNode(ruleSelectedId);
        const empty = $("#ruleEditorEmpty");
        const panel = $("#ruleEditorPanel");
        if (!found) {
            const hasRule = Boolean(currentRuleJudge() && judgeParts(currentRuleJudge()).rule);
            $("#ruleEditorEmptyTitle").textContent = hasRule ? "选择一个规则节点" : "尚未配置条件";
            $("#ruleEditorEmptyHint").textContent = hasRule
                ? "选择左侧节点后在这里查看规则信息。"
                : "请从左侧空状态配置第一条规则。";
            empty.hidden = false;
            panel.hidden = true;
            return;
        }
        const { node } = found;
        empty.hidden = true;
        panel.hidden = false;
        $("#ruleEditorTitle").textContent = ruleNodeDisplayName(node);
        $("#ruleNodeType").value = TYPES[node.type].name;
        const aliasInput = $("#ruleNodeLabel");
        const fallbackName = ruleNodeDisplayName(node);
        aliasInput.value = node.name?.trim() || "";
        aliasInput.placeholder = fallbackName;
        aliasInput.dataset.initialAlias = node.name || "";
        aliasInput.dataset.fallbackName = fallbackName;
        $("#ruleNodeNameField").hidden = !node.name?.trim();
        $("#ruleNodeExpression").value = node.expression || "";
        const atomic = node.type === "R";
        const logic = node.type === "L";
        $("#ruleDefinitionField").hidden = !atomic;
        $("#ruleLogicField").hidden = !logic;
        $("#ruleReferenceField").hidden = atomic || logic;
        $("#ruleReferenceLabel").textContent = "命中表达式";
        $("#ruleNodeExpression").readOnly = true;
        if (atomic) populateRuleDefinitionSelect($("#ruleDefinitionSelect"), node.expression);
        if (logic) {
            $("#ruleLogicSelect").value = node.expression === "||" ? "||" : "&&";
        }
        $("#ruleNodeLabel").readOnly = true;
        $("#ruleDefinitionSelect").disabled = true;
        $("#ruleLogicSelect").disabled = true;
        $("#ruleNodeExpression").readOnly = true;
        ruleEditorEditing = false;
        ruleEditorDraftNodeId = null;
        $("#ruleEditorEditButton").hidden = false;
        $("#ruleEditorCancelButton").hidden = true;
        $("#ruleEditorSaveButton").hidden = true;
        $("#ruleEditorSaveButton").disabled = true;
    }

    function enterRuleEditorEditMode() {
        const found = findRuleNode(ruleSelectedId);
        if (!found) return;
        ruleEditorEditing = true;
        ruleEditorDraftNodeId = found.node.id;
        $("#ruleNodeNameField").hidden = false;
        $("#ruleNodeLabel").readOnly = false;
        $("#ruleDefinitionSelect").disabled = found.node.type !== "R";
        $("#ruleLogicSelect").disabled = found.node.type !== "L";
        $("#ruleNodeExpression").readOnly = found.node.type !== "H";
        $("#ruleEditorEditButton").hidden = true;
        $("#ruleEditorCancelButton").hidden = false;
        $("#ruleEditorSaveButton").hidden = false;
        updateRuleEditorDirtyState();
        $("#ruleNodeLabel").focus();
        $("#ruleNodeLabel").select();
    }

    function ruleEditorDraft() {
        const found = findRuleNode(ruleSelectedId);
        if (!found || found.node.id !== ruleEditorDraftNodeId) return null;
        const aliasInput = $("#ruleNodeLabel");
        const value = aliasInput.value.trim();
        const initialAlias = aliasInput.dataset.initialAlias || "";
        const fallbackName = aliasInput.dataset.fallbackName || "";
        const name = !initialAlias.trim() && value === fallbackName ? "" : value;
        let expression = found.node.expression || "";
        if (found.node.type === "R") expression = $("#ruleDefinitionSelect").value;
        if (found.node.type === "L") expression = $("#ruleLogicSelect").value;
        if (found.node.type === "H") expression = $("#ruleNodeExpression").value.trim();
        return { found, name, expression };
    }

    function ruleEditorHasUnsavedChanges() {
        if (!ruleEditorEditing) return false;
        const draft = ruleEditorDraft();
        return Boolean(draft && (
            draft.name !== (draft.found.node.name || "").trim()
            || draft.expression !== (draft.found.node.expression || "")
        ));
    }

    function updateRuleEditorDirtyState() {
        $("#ruleEditorSaveButton").disabled = !ruleEditorHasUnsavedChanges();
    }

    function updateRuleDefinitionDraft() {
        const aliasInput = $("#ruleNodeLabel");
        const initialAlias = aliasInput.dataset.initialAlias || "";
        const definition = ruleDefinitionById($("#ruleDefinitionSelect").value);
        if (!initialAlias.trim() && !aliasInput.value.trim() && definition) {
            aliasInput.placeholder = definition.desc;
            aliasInput.dataset.fallbackName = definition.desc;
        }
        updateRuleEditorDirtyState();
    }

    function saveRuleEditorChanges({ refresh = true } = {}) {
        const draft = ruleEditorDraft();
        if (!draft || !ruleEditorHasUnsavedChanges()) return false;
        draft.found.node.name = draft.name;
        draft.found.node.expression = draft.expression;
        if (refresh) refreshRuleAfterEdit(draft.found);
        return true;
    }

    function cancelRuleEditorChanges() {
        if (!ruleEditorEditing) return;
        updateRuleEditor();
    }

    function finishRuleEditorCancel() {
        if (closeRuleDialogAfterEdit) {
            ruleDialog.close();
            return;
        }
        cancelRuleEditorChanges();
    }

    function finishRuleEditorSave() {
        const closeAfterSave = closeRuleDialogAfterEdit;
        if (!saveRuleEditorChanges({ refresh: !closeAfterSave })) return;
        if (closeAfterSave) ruleDialog.close();
    }

    function confirmDiscardRuleEditorChanges() {
        if (!ruleEditorEditing) return true;
        if (ruleEditorHasUnsavedChanges() && !window.confirm("放弃未保存的修改？")) return false;
        cancelRuleEditorChanges();
        return true;
    }

    function renderRuleDialog() {
        const judge = currentRuleJudge();
        if (!judge) return;
        const name = ruleDisplayName(judge);
        $("#ruleDialogTitle").textContent = name;
        renderRuleTree();
        updateRuleEditor();
    }

    function openRuleDialog(judgeId, { edit = false } = {}) {
        const found = findNode(judgeId);
        if (!found || found.node.type !== "J") return;
        ruleJudgeId = judgeId;
        ruleSelectedId = judgeParts(found.node).rule?.id || null;
        closeRuleDialogAfterEdit = edit;
        hideRulePopover();
        renderRuleDialog();
        if (!ruleDialog.open) ruleDialog.showModal();
        if (edit) enterRuleEditorEditMode();
    }

    function hideRulePopover() {
        const popover = $("#ruleContextPopover");
        popover.hidden = true;
        popover.querySelectorAll(".rule-context-section").forEach((section) => {
            section.hidden = true;
        });
        popover.classList.remove("menu-mode");
        rulePopoverAnchorId = null;
        rulePopoverPoint = null;
    }

    function positionRulePopover() {
        const popover = $("#ruleContextPopover");
        if (popover.hidden) return;
        const panel = $("#ruleTreePanel");
        const panelRect = panel.getBoundingClientRect();
        const gap = 12;
        const anchor = rulePopoverPoint ? null : (rulePopoverAnchorId
            ? ruleTreeRoot.querySelector(`[data-rule-node-id="${rulePopoverAnchorId}"]`)
            : $("#ruleAddRootButton"));
        if (!rulePopoverPoint && !anchor) {
            hideRulePopover();
            return;
        }
        const anchorRect = anchor?.getBoundingClientRect();
        let left = rulePopoverPoint
            ? rulePopoverPoint.x - panelRect.left
            : anchorRect.right - panelRect.left + gap;
        if (left + popover.offsetWidth > panel.clientWidth - gap) {
            left = rulePopoverPoint
                ? panel.clientWidth - popover.offsetWidth - gap
                : anchorRect.left - panelRect.left - popover.offsetWidth - gap;
        }
        left = Math.max(gap, Math.min(left, panel.clientWidth - popover.offsetWidth - gap));
        const top = Math.max(46, Math.min(
            rulePopoverPoint ? rulePopoverPoint.y - panelRect.top : anchorRect.top - panelRect.top,
            panel.clientHeight - popover.offsetHeight - gap
        ));
        popover.style.left = `${left}px`;
        popover.style.top = `${top}px`;
    }

    function showRulePopover(sectionId, anchorId = ruleSelectedId) {
        const popover = $("#ruleContextPopover");
        popover.querySelectorAll(".rule-context-section").forEach((section) => {
            section.hidden = section.id !== sectionId;
        });
        popover.classList.toggle("menu-mode", sectionId === "ruleActionPanel");
        rulePopoverAnchorId = anchorId;
        popover.hidden = false;
        requestAnimationFrame(positionRulePopover);
    }

    function startRuleActions(point) {
        const found = findRuleNode(ruleSelectedId);
        if (!found) return;
        const canInsertSibling = ["L", "H"].includes(found.parent?.type);
        $("#ruleActionBefore").hidden = !canInsertSibling;
        $("#ruleActionAfter").hidden = !canInsertSibling;
        $("#ruleActionExtend").hidden = found.node.type !== "R";
        $("#ruleActionDelete").disabled = !canDeleteRule(found);
        rulePopoverPoint = point;
        showRulePopover("ruleActionPanel", found.node.id);
    }

    function ruleDefinitionById(ruleId) {
        return RULE_DEFINITIONS.find((definition) => definition.ruleId === ruleId) || null;
    }

    function actionDefinitionById(ruleId) {
        return ACTION_DEFINITIONS.find((definition) => definition.ruleId === ruleId) || null;
    }

    function populateFlowDefinitionSelect(select, definitions, selectedRuleId, selectedLabel) {
        const current = selectedRuleId && !definitions.some((definition) => definition.ruleId === selectedRuleId)
            ? [{ ruleId: selectedRuleId, desc: selectedLabel || `${selectedRuleId}（当前引用）`, useType: null }]
            : [];
        select.innerHTML = [...current, ...definitions]
            .map((definition) => `<option value="${escapeText(definition.ruleId)}">${escapeText(definition.desc)} · ${escapeText(definition.ruleId)}</option>`)
            .join("");
        select.value = selectedRuleId || definitions[0]?.ruleId || "";
    }

    function populateRuleDefinitionSelect(select, selectedRuleId = "") {
        const current = selectedRuleId && !ruleDefinitionById(selectedRuleId)
            ? [{ ruleId: selectedRuleId, desc: `${selectedRuleId}（当前引用）`, useType: null }]
            : [];
        select.innerHTML = [...current, ...RULE_DEFINITIONS]
            .map((definition) => `<option value="${escapeText(definition.ruleId)}">${escapeText(definition.desc)} · ${escapeText(definition.ruleId)}</option>`)
            .join("");
        select.value = selectedRuleId || RULE_DEFINITIONS[0]?.ruleId || "";
    }

    function createAtomicRule(ruleId, name = "") {
        const definition = ruleDefinitionById(ruleId);
        if (!definition) return null;
        return {
            id: `n${nextId++}`,
            type: "R",
            name: name.trim(),
            expression: definition.ruleId,
            relation: "rule",
            children: []
        };
    }

    function createLogicRule(operator, name, ruleIds) {
        const rules = ruleIds.map((ruleId) => createAtomicRule(ruleId));
        if (rules.some((rule) => !rule)) return null;
        return {
            id: `n${nextId++}`,
            type: "L",
            name: name.trim(),
            expression: operator,
            relation: "rule",
            children: rules
        };
    }

    function renderRuleAddRows(focusIndex = -1) {
        $("#ruleAddRows").innerHTML = ruleAddRuleIds.map((ruleId, index) => `
            <div class="rule-add-row">
                <label>
                    引用规则 ${index + 1}
                    <select data-rule-add-select="${index}">
                        ${RULE_DEFINITIONS.map((definition) => `
                            <option value="${escapeText(definition.ruleId)}" ${definition.ruleId === ruleId ? "selected" : ""}>
                                ${escapeText(definition.desc)} · ${escapeText(definition.ruleId)}
                            </option>`).join("")}
                    </select>
                </label>
                <button type="button" data-rule-add-remove="${index}" aria-label="删除规则 ${index + 1}" ${ruleAddRuleIds.length === 1 ? "disabled" : ""}>×</button>
            </div>`).join("");
        $("#ruleAddLogicField").hidden = ruleAddRuleIds.length <= 1;
        requestAnimationFrame(() => {
            positionRulePopover();
            if (focusIndex >= 0) {
                $(`[data-rule-add-select="${focusIndex}"]`)?.focus();
            }
        });
    }

    function appendRuleAddRow() {
        const unused = RULE_DEFINITIONS.find((definition) => !ruleAddRuleIds.includes(definition.ruleId));
        ruleAddRuleIds.push(unused?.ruleId || RULE_DEFINITIONS[0]?.ruleId || "");
        renderRuleAddRows(ruleAddRuleIds.length - 1);
    }

    function removeRuleAddRow(index) {
        if (ruleAddRuleIds.length <= 1 || index < 0 || index >= ruleAddRuleIds.length) return;
        ruleAddRuleIds.splice(index, 1);
        renderRuleAddRows(Math.min(index, ruleAddRuleIds.length - 1));
    }

    function startRuleAdd(mode) {
        const judge = currentRuleJudge();
        const found = findRuleNode(ruleSelectedId);
        if (!judge) return;
        if (mode === "root" && judgeParts(judge).rule) return;
        const parent = ["before", "after"].includes(mode) ? found?.parent : null;
        if (mode !== "root" && !["L", "H"].includes(parent?.type)) return;
        ruleAddMode = mode;
        ruleAddParentId = parent?.id || null;
        ruleAddAnchorId = mode === "root" ? null : found.node.id;
        $("#ruleAddTitle").textContent = mode === "root"
            ? "配置根规则"
            : (mode === "before" ? "前插规则" : "后插规则");

        ruleAddRuleIds = [RULE_DEFINITIONS[0]?.ruleId || ""];
        document.querySelector('input[name="ruleAddLogic"][value="&&"]').checked = true;
        showRulePopover("ruleAddPanel", mode === "root" ? null : found.node.id);
        renderRuleAddRows(0);
    }

    function cancelRuleAdd() {
        ruleAddParentId = null;
        ruleAddAnchorId = null;
        ruleAddRuleIds = [];
        hideRulePopover();
    }

    function confirmRuleAdd() {
        const judge = currentRuleJudge();
        if (!judge || ruleAddRuleIds.length === 0) return;
        const node = ruleAddRuleIds.length === 1
            ? createAtomicRule(ruleAddRuleIds[0])
            : createLogicRule(
                document.querySelector('input[name="ruleAddLogic"]:checked').value,
                "",
                ruleAddRuleIds
            );
        if (!node) return;

        if (ruleAddMode === "root") {
            judge.children.unshift(node);
        } else {
            const parent = findRuleNode(ruleAddParentId)?.node;
            if (!parent || !["L", "H"].includes(parent.type)) return;
            const anchorIndex = parent.children.findIndex((child) => child.id === ruleAddAnchorId);
            if (anchorIndex < 0) return;
            const insertIndex = ruleAddMode === "after" ? anchorIndex + 1 : anchorIndex;
            parent.children.splice(insertIndex, 0, node);
        }
        ruleSelectedId = node.id;
        ruleAddParentId = null;
        ruleAddAnchorId = null;
        ruleAddRuleIds = [];
        hideRulePopover();
        renderRuleDialog();
        render({ preserveView: true });
    }

    function renderRuleExtendRows(focusIndex = -1) {
        $("#ruleExtendRows").innerHTML = ruleExtendRuleIds.map((ruleId, index) => `
            <div class="rule-add-row">
                <label>
                    引用规则 ${index + 1}
                    <select data-rule-extend-select="${index}">
                        ${RULE_DEFINITIONS.map((definition) => `
                            <option value="${escapeText(definition.ruleId)}" ${definition.ruleId === ruleId ? "selected" : ""}>
                                ${escapeText(definition.desc)} · ${escapeText(definition.ruleId)}
                            </option>`).join("")}
                    </select>
                </label>
                <button type="button" data-rule-extend-remove="${index}" aria-label="删除新增规则 ${index + 1}" ${ruleExtendRuleIds.length === 1 ? "disabled" : ""}>×</button>
            </div>`).join("");
        requestAnimationFrame(() => {
            positionRulePopover();
            if (focusIndex >= 0) {
                $(`[data-rule-extend-select="${focusIndex}"]`)?.focus();
            }
        });
    }

    function appendRuleExtendRow() {
        const found = findRuleNode(ruleSelectedId);
        const excluded = new Set([found?.node.expression, ...ruleExtendRuleIds]);
        const unused = RULE_DEFINITIONS.find((definition) => !excluded.has(definition.ruleId));
        ruleExtendRuleIds.push(unused?.ruleId || RULE_DEFINITIONS[0]?.ruleId || "");
        renderRuleExtendRows(ruleExtendRuleIds.length - 1);
    }

    function removeRuleExtendRow(index) {
        if (ruleExtendRuleIds.length <= 1 || index < 0 || index >= ruleExtendRuleIds.length) return;
        ruleExtendRuleIds.splice(index, 1);
        renderRuleExtendRows(Math.min(index, ruleExtendRuleIds.length - 1));
    }

    function startRuleExtend() {
        const found = findRuleNode(ruleSelectedId);
        if (!found || found.node.type !== "R") return;
        const definition = ruleDefinitionById(found.node.expression);
        $("#ruleExtendCurrent").textContent = `${definition?.desc || ruleNodeDisplayName(found.node)} · ${found.node.expression}`;
        const alternate = RULE_DEFINITIONS.find((candidate) => candidate.ruleId !== found.node.expression);
        ruleExtendRuleIds = [alternate?.ruleId || RULE_DEFINITIONS[0]?.ruleId || ""];
        document.querySelector('input[name="ruleExtendLogic"][value="&&"]').checked = true;
        showRulePopover("ruleExtendPanel", found.node.id);
        renderRuleExtendRows(0);
    }

    function cancelRuleExtend() {
        ruleExtendRuleIds = [];
        hideRulePopover();
    }

    function saveRuleExtend() {
        const found = findRuleNode(ruleSelectedId);
        if (!found || found.node.type !== "R" || ruleExtendRuleIds.length === 0) return;
        const additions = ruleExtendRuleIds.map((ruleId) => createAtomicRule(ruleId));
        if (additions.some((rule) => !rule)) return;
        const current = found.node;
        const parent = found.parent;
        const currentIndex = parent.children.findIndex((child) => child.id === current.id);
        if (currentIndex < 0) return;
        const composite = {
            id: `n${nextId++}`,
            type: "L",
            name: "",
            expression: document.querySelector('input[name="ruleExtendLogic"]:checked').value,
            relation: current.relation,
            children: [current, ...additions]
        };
        current.relation = "rule";
        parent.children.splice(currentIndex, 1, composite);
        ruleSelectedId = composite.id;
        ruleExtendRuleIds = [];
        hideRulePopover();
        renderRuleDialog();
        render({ preserveView: true });
    }

    function refreshRuleAfterEdit(found, updateEditor = true) {
        if (!found) return;
        const judge = currentRuleJudge();
        const root = judge ? judgeParts(judge).rule : null;
        if (root?.id === found.node.id) {
            $("#ruleDialogTitle").textContent = ruleDisplayName(judge);
        }
        renderRuleTree();
        if (updateEditor) updateRuleEditor();
        render({ preserveView: true });
        requestAnimationFrame(positionRulePopover);
    }

    function deleteSelectedRule() {
        const found = findRuleNode(ruleSelectedId);
        if (!canDeleteRule(found)) return;
        const descendants = countNodes(found.node) - 1;
        const name = ruleNodeDisplayName(found.node);
        const message = descendants ? `删除「${name}」及其 ${descendants} 个子规则？` : `删除「${name}」？`;
        if (!window.confirm(message)) return;
        hideRulePopover();
        found.parent.children = found.parent.children.filter((child) => child.id !== found.node.id);
        ruleSelectedId = ["L", "H"].includes(found.parent.type) ? found.parent.id : null;
        renderRuleDialog();
        render({ preserveView: true });
    }

    function populateNewNodeTypes() {
        const types = RELATIONS[addRelation]?.types || [];
        populateTypeSelect($("#newNodeType"), types);
        $("#newNodeType").value = types.includes("A") ? "A" : types[0];
        updateNewNodeFields();
    }

    function newNodeDefinitions(nodeType) {
        if (nodeType === "J") return RULE_DEFINITIONS;
        if (nodeType === "A") return ACTION_DEFINITIONS;
        return [];
    }

    function populateNewNodeDefinitions() {
        const definitions = newNodeDefinitions($("#newNodeType").value);
        const field = $("#newNodeDefinitionField");
        const select = $("#newNodeDefinition");
        field.hidden = definitions.length === 0;
        select.disabled = definitions.length === 0;
        select.required = definitions.length > 0;
        select.innerHTML = definitions
            .map((definition) => `
                <option value="${escapeText(definition.ruleId)}">
                    ${escapeText(definition.desc)} · ${escapeText(definition.ruleId)}
                </option>`)
            .join("");
    }

    function populateNewNodeAnchors(parent) {
        const positionable = ["branch", "decision"].includes(addRelation);
        const children = positionable
            ? parent.children.filter((child) => child.relation === addRelation)
            : [];
        const placementField = $("#newNodePlacementField");
        const anchorField = $("#newNodeAnchorField");
        const placement = $("#newNodePlacement");
        const anchor = $("#newNodeAnchor");
        const showPosition = children.length > 0;
        placementField.hidden = !showPosition;
        anchorField.hidden = !showPosition;
        placement.disabled = !showPosition;
        anchor.disabled = !showPosition;
        anchor.required = showPosition;
        anchor.innerHTML = children
            .map((child, index) => `
                <option value="${escapeText(child.id)}">
                    ${index + 1}. ${escapeText(flowNodeDisplayName(child))}
                </option>`)
            .join("");
        placement.value = "after";
    }

    function updateNewNodeFields() {
        populateNewNodeDefinitions();
    }

    function openAddDialog(relation) {
        const found = findNode(selectedId);
        if (!found) return;
        const operation = flowNodeOperations(found.node).find((item) => item.relation === relation);
        if (!operation) return;
        addRelation = relation;
        $("#dialogTitle").textContent = operation.dialogTitle;
        populateNewNodeTypes();
        populateNewNodeAnchors(found.node);
        dialog.showModal();
        requestAnimationFrame(() => {
            $("#newNodeType").focus();
        });
    }

    function addNode() {
        const found = findNode(selectedId);
        if (!found || !availableRelations(found.node).includes(addRelation)) return;
        const parent = found.node;
        const type = $("#newNodeType").value;
        const definitionId = $("#newNodeDefinition").value;
        const definition = newNodeDefinitions(type)
            .find((candidate) => candidate.ruleId === definitionId);
        if (["A", "J"].includes(type) && !definition) return;
        const node = {
            id: `n${nextId++}`,
            type,
            relation: addRelation,
            children: []
        };
        if (type === "A") {
            node.label = definition.desc;
            node.expression = definition.ruleId;
        } else if (type === "J") {
            const rule = createAtomicRule(definition.ruleId);
            if (!rule) return;
            node.children.push(rule);
        }
        parent.collapsed = false;
        const anchorId = $("#newNodeAnchor").disabled ? "" : $("#newNodeAnchor").value;
        const anchorIndex = parent.children.findIndex((child) =>
            child.id === anchorId && child.relation === addRelation);
        if (anchorIndex >= 0) {
            const insertIndex = $("#newNodePlacement").value === "before"
                ? anchorIndex
                : anchorIndex + 1;
            parent.children.splice(insertIndex, 0, node);
        } else {
            parent.children.push(node);
        }
        selectedId = node.id;
        fitMode = false;
        render({ preserveView: true });
        return node;
    }

    function deleteSelected() {
        const found = findNode(selectedId);
        if (!found || !canDeleteFlowNode(found.node, found.parent)) return;
        const descendants = countFlowNodes(found.node) - 1;
        const nodeName = flowNodeDisplayName(found.node);
        deleteNodeId = found.node.id;
        $("#deleteNodeMessage").textContent = descendants
            ? `确定删除「${nodeName}」及其 ${descendants} 个流程子节点吗？`
            : `确定删除「${nodeName}」吗？`;
        deleteNodeDialog.showModal();
    }

    function confirmDeleteSelected() {
        const found = findNode(deleteNodeId);
        if (!found || !canDeleteFlowNode(found.node, found.parent)) return false;
        found.parent.children = found.parent.children.filter((child) => child.id !== found.node.id);
        selectedId = found.parent.id;
        deleteNodeId = null;
        render({ preserveView: true });
        return true;
    }

    function toggleCollapse() {
        const found = findNode(selectedId);
        if (!found) return;
        if (!found.node.children.length) return;
        found.node.collapsed = !found.node.collapsed;
        render({ preserveView: true });
    }

    function applyTransform() {
        stage.style.transform = `translate(${panX}px, ${panY}px) scale(${scale})`;
        $("#zoomValue").value = `${Math.round(scale * 100)}%`;
        $("#zoomValue").textContent = `${Math.round(scale * 100)}%`;
    }

    function setZoom(nextScale, originX = viewport.clientWidth / 2, originY = viewport.clientHeight / 2) {
        const clamped = Math.min(1.6, Math.max(.35, nextScale));
        const worldX = (originX - panX) / scale;
        const worldY = (originY - panY) / scale;
        panX = originX - worldX * clamped;
        panY = originY - worldY * clamped;
        scale = clamped;
        fitMode = false;
        applyTransform();
    }

    function fitTree() {
        const width = treeRoot.offsetWidth + 140;
        const height = treeRoot.offsetHeight + 140;
        if (!width || !height) return;
        scale = Math.min(1.12, Math.max(.35, Math.min((viewport.clientWidth - 46) / width, (viewport.clientHeight - 46) / height)));
        panX = Math.max(12, (viewport.clientWidth - width * scale) / 2);
        panY = Math.max(12, (viewport.clientHeight - height * scale) / 2);
        fitMode = true;
        applyTransform();
    }

    treeRoot.addEventListener("click", (event) => {
        const shell = event.target.closest(".node-shell");
        if (!shell) return;
        selectNode(shell.dataset.nodeId);
        const action = event.target.closest("[data-action]")?.dataset.action;
        if (action === "collapse") toggleCollapse();
    });

    treeRoot.addEventListener("keydown", (event) => {
        if ((event.key === "Enter" || event.key === " ") && event.target.matches(".node-shell")) {
            event.preventDefault();
            selectNode(event.target.dataset.nodeId);
        }
    });

    ruleTreeRoot.addEventListener("click", (event) => {
        if (event.target.closest("#ruleAddRootButton")) {
            if (!confirmDiscardRuleEditorChanges()) return;
            startRuleAdd("root");
            return;
        }
        const shell = event.target.closest(".node-shell");
        if (!shell) {
            if (!confirmDiscardRuleEditorChanges()) return;
            ruleSelectedId = null;
            hideRulePopover();
            renderRuleTree();
            updateRuleEditor();
            return;
        }
        const nextRuleId = shell.dataset.ruleNodeId;
        if (nextRuleId !== ruleSelectedId && !confirmDiscardRuleEditorChanges()) return;
        ruleSelectedId = nextRuleId;
        hideRulePopover();
        if (ruleEditorEditing) return;
        renderRuleTree();
        updateRuleEditor();
        ruleTreeRoot.querySelector(`[data-rule-node-id="${ruleSelectedId}"]`)?.focus({ preventScroll: true });
    });

    ruleTreeRoot.addEventListener("contextmenu", (event) => {
        const shell = event.target.closest(".node-shell");
        if (!shell) return;
        event.preventDefault();
        const nextRuleId = shell.dataset.ruleNodeId;
        if (nextRuleId !== ruleSelectedId && !confirmDiscardRuleEditorChanges()) return;
        ruleSelectedId = nextRuleId;
        hideRulePopover();
        if (!ruleEditorEditing) {
            renderRuleTree();
            updateRuleEditor();
        }
        startRuleActions({ x: event.clientX, y: event.clientY });
    });

    ruleTreeRoot.addEventListener("keydown", (event) => {
        if ((event.key === "Enter" || event.key === " ") && event.target.matches(".node-shell")) {
            event.preventDefault();
            const nextRuleId = event.target.dataset.ruleNodeId;
            if (nextRuleId !== ruleSelectedId && !confirmDiscardRuleEditorChanges()) return;
            ruleSelectedId = nextRuleId;
            if (ruleEditorEditing) return;
            renderRuleTree();
            updateRuleEditor();
            ruleTreeRoot.querySelector(`[data-rule-node-id="${ruleSelectedId}"]`)?.focus({ preventScroll: true });
        }
    });

    viewport.addEventListener("pointerdown", (event) => {
        if (event.button !== 0 || event.target.closest(".node-shell")) return;
        selectNode(null);
        drag = { x: event.clientX, y: event.clientY, panX, panY };
        viewport.classList.add("dragging");
        viewport.setPointerCapture(event.pointerId);
    });
    viewport.addEventListener("pointermove", (event) => {
        if (!drag) return;
        panX = drag.panX + event.clientX - drag.x;
        panY = drag.panY + event.clientY - drag.y;
        fitMode = false;
        applyTransform();
    });
    viewport.addEventListener("pointerup", () => { drag = null; viewport.classList.remove("dragging"); });
    viewport.addEventListener("pointercancel", () => { drag = null; viewport.classList.remove("dragging"); });
    viewport.addEventListener("wheel", (event) => {
        event.preventDefault();
        const rect = viewport.getBoundingClientRect();
        if (event.ctrlKey || event.metaKey) {
            setZoom(scale * Math.exp(-event.deltaY * .002), event.clientX - rect.left, event.clientY - rect.top);
        } else {
            panX -= event.deltaX;
            panY -= event.deltaY;
            fitMode = false;
            applyTransform();
        }
    }, { passive: false });

    $("#flowNodeActions").addEventListener("click", (event) => {
        const relation = event.target.closest("[data-flow-relation]")?.dataset.flowRelation;
        if (relation) openAddDialog(relation);
    });
    $("#zoomOutButton").addEventListener("click", () => setZoom(scale - .1));
    $("#zoomInButton").addEventListener("click", () => setZoom(scale + .1));
    $("#fitButton").addEventListener("click", fitTree);
    $("#resetButton").addEventListener("click", () => {
        if (!window.confirm("恢复初始示例树？当前修改将丢失。")) return;
        tree = sampleTree();
        selectedId = null;
        ruleJudgeId = null;
        ruleSelectedId = null;
        nextId = 44;
        render({ preserveView: false });
    });
    $("#deleteButton").addEventListener("click", deleteSelected);
    $("#editNodeButton").addEventListener("click", openSelectedNodeEditor);

    $("#ruleDialogCloseButton").addEventListener("click", () => {
        if (confirmDiscardRuleEditorChanges()) ruleDialog.close();
    });
    $("#ruleActionPanel").addEventListener("click", (event) => {
        const action = event.target.closest("[data-rule-menu-action]")?.dataset.ruleMenuAction;
        if (!action) return;
        if (action === "edit") {
            hideRulePopover();
            enterRuleEditorEditMode();
            return;
        }
        if (!confirmDiscardRuleEditorChanges()) return;
        if (action === "before") startRuleAdd("before");
        else if (action === "after") startRuleAdd("after");
        else if (action === "extend") startRuleExtend();
        else if (action === "delete") deleteSelectedRule();
    });
    $("#ruleExtendCloseButton").addEventListener("click", cancelRuleExtend);
    $("#ruleExtendCancelButton").addEventListener("click", cancelRuleExtend);
    $("#ruleExtendSaveButton").addEventListener("click", saveRuleExtend);
    $("#ruleExtendRowButton").addEventListener("click", appendRuleExtendRow);
    $("#ruleExtendRows").addEventListener("change", (event) => {
        const index = Number(event.target.dataset.ruleExtendSelect);
        if (Number.isInteger(index) && ruleExtendRuleIds[index] !== undefined) {
            ruleExtendRuleIds[index] = event.target.value;
        }
    });
    $("#ruleExtendRows").addEventListener("click", (event) => {
        const button = event.target.closest("[data-rule-extend-remove]");
        if (button) removeRuleExtendRow(Number(button.dataset.ruleExtendRemove));
    });
    $("#ruleAddCancelButton").addEventListener("click", cancelRuleAdd);
    $("#ruleAddConfirmButton").addEventListener("click", confirmRuleAdd);
    $("#ruleAddRowButton").addEventListener("click", appendRuleAddRow);
    $("#ruleAddRows").addEventListener("change", (event) => {
        const index = Number(event.target.dataset.ruleAddSelect);
        if (Number.isInteger(index) && ruleAddRuleIds[index] !== undefined) {
            ruleAddRuleIds[index] = event.target.value;
        }
    });
    $("#ruleAddRows").addEventListener("click", (event) => {
        const button = event.target.closest("[data-rule-add-remove]");
        if (button) removeRuleAddRow(Number(button.dataset.ruleAddRemove));
    });
    $("#ruleEditorEditButton").addEventListener("click", enterRuleEditorEditMode);
    $("#ruleEditorCancelButton").addEventListener("click", finishRuleEditorCancel);
    $("#ruleEditorSaveButton").addEventListener("click", finishRuleEditorSave);
    $("#ruleNodeLabel").addEventListener("input", updateRuleEditorDirtyState);
    $("#ruleDefinitionSelect").addEventListener("change", updateRuleDefinitionDraft);
    $("#ruleLogicSelect").addEventListener("change", updateRuleEditorDirtyState);
    $("#ruleNodeExpression").addEventListener("input", updateRuleEditorDirtyState);
    ruleDialog.addEventListener("cancel", (event) => {
        if (!confirmDiscardRuleEditorChanges()) event.preventDefault();
    });
    ruleDialog.addEventListener("close", () => {
        ruleEditorEditing = false;
        ruleEditorDraftNodeId = null;
        closeRuleDialogAfterEdit = false;
        ruleJudgeId = null;
        ruleSelectedId = null;
        hideRulePopover();
        render({ preserveView: true });
    });
    ruleDialog.addEventListener("pointerdown", (event) => {
        if ($("#ruleContextPopover").hidden) return;
        if (event.target.closest("#ruleContextPopover, .node-shell, #ruleAddRootButton")) return;
        hideRulePopover();
    });
    $(".rule-tree-viewport").addEventListener("scroll", hideRulePopover, { passive: true });
    window.addEventListener("resize", positionRulePopover);

    document.addEventListener("pointerdown", (event) => {
        if (event.target.closest(".node-shell")) return;
        document.activeElement?.closest?.(".node-shell")?.blur();
    }, true);

    $("#newNodeType").addEventListener("change", updateNewNodeFields);
    $("#nodeDialogForm").addEventListener("submit", (event) => {
        if (event.submitter?.value !== "default") return;
        event.preventDefault();
        if (!$("#nodeDialogForm").reportValidity()) return;
        const node = addNode();
        dialog.close();
        if (node?.type === "J") requestAnimationFrame(() => openRuleDialog(node.id));
    });
    $("#nodeEditDialogForm").addEventListener("submit", (event) => {
        if (event.submitter?.value !== "default") return;
        event.preventDefault();
        saveSelectedNodeEditor();
        nodeEditDialog.close();
    });
    $("#deleteNodeDialogForm").addEventListener("submit", (event) => {
        if (event.submitter?.value !== "default") return;
        event.preventDefault();
        if (confirmDeleteSelected()) deleteNodeDialog.close();
    });
    deleteNodeDialog.addEventListener("close", () => {
        deleteNodeId = null;
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && dialog.open) dialog.close();
        const editing = event.target.matches("input, select, textarea");
        if (!editing && ruleDialog.open && (event.key === "Delete" || event.key === "Backspace")) {
            deleteSelectedRule();
        }
        if (!editing && event.key === "0") fitTree();
    });

    new ResizeObserver(() => { if (fitMode) fitTree(); }).observe(viewport);
    render({ preserveView: false });
})();
