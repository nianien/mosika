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
    let inspectorEditingField = null;
    let nextId = 44;
    let addRelation = null;
    let insertBeforeMode = false;
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
    let rulePopoverAnchorId = null;
    let rulePopoverPoint = null;
    let ruleEditorEditing = false;
    let ruleEditorDraftNodeId = null;
    let ruleEditorDraftState = null;
    let closeRuleDialogAfterEdit = false;

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
    const confirmDialog = $("#confirmDialog");
    const ruleDialog = $("#ruleDialog");
    const ruleTreeRoot = $("#ruleTreeRoot");

    function walk(node, visitor, parent = null) {
        if (visitor(node, parent) === false) return false;
        for (const child of node.children) {
            if (walk(child, visitor, node) === false) return false;
        }
        return true;
    }

    function openConfirm(message, { title = "确认操作", confirmLabel = "确定" } = {}) {
        $("#confirmDialogTitle").textContent = title;
        $("#confirmDialogMessage").textContent = message;
        $("#confirmDialogConfirm").textContent = confirmLabel;
        return new Promise((resolve) => {
            const onClose = () => {
                confirmDialog.removeEventListener("close", onClose);
                resolve(confirmDialog.returnValue === "default");
            };
            confirmDialog.addEventListener("close", onClose);
            confirmDialog.showModal();
        });
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
            add("next", "添加流程", "添加流程");
        } else if (["C", "J"].includes(node.type)) {
            add("action", "添加流程", "添加流程");
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
        const collapsedBadge = collapsed ? `<span class="collapsed-count" title="${node.children.length} 个分支">${node.children.length}</span>` : "";
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
                : (flowNodeName(node) ? `${type.name} · ${flowNodeName(node)}` : type.name));
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

    function inspectorEditableType(type) {
        return ["A", "C", "S", "P", "D", "J"].includes(type);
    }

    function editKey(nodeId, field) {
        return `${nodeId}:${field}`;
    }

    function renderInspectorHeading(node) {
        const container = $("#inspectorHeading");
        if (!container) return;
        container.innerHTML = `<strong id="inspectorTitle">节点属性</strong>`;
    }

    function renderInspectorFields(node) {
        const nodeName = flowNodeName(node);
        const parts = [];
        const pencil = (field) => `<button class="field-edit" type="button" data-field-edit="${field}" aria-label="编辑" title="编辑">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20h4L18.5 9.5a2 2 0 0 0-2.83-2.83L5 17v3z"/><path d="M13.5 6.5l4 4"/></svg>
        </button>`;
        const confirmCancel = () => `<div class="field-edit-actions">
            <button class="field-confirm" type="button" data-field-confirm aria-label="确认" title="确认"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 13l4 4L19 7"/></svg></button>
            <button class="field-cancel" type="button" data-field-cancel aria-label="取消" title="取消"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg></button>
        </div>`;
        if (inspectorEditableType(node.type)) {
            const aliasActive = inspectorEditingField === editKey(node.id, "alias");
            parts.push(`<div class="detail-field editable${aliasActive ? " field-editing" : ""}" data-field="alias">
                <span class="detail-label">节点名称</span>
                <div class="field-row">
                    <span class="detail-value">${escapeText(nodeName || "未设置")}</span>
                    ${aliasActive ? "" : pencil("alias")}
                    <input class="field-control" id="inspectorAliasInput" maxlength="28" autocomplete="off"
                           value="${escapeText(nodeName)}" placeholder="为该节点设置名称">
                    ${aliasActive ? confirmCancel() : ""}
                </div>
            </div>`);
        }
        const typeEditable = ["S", "P"].includes(node.type);
        const typeActive = typeEditable && inspectorEditingField === editKey(node.id, "type");
        parts.push(`<div class="detail-field${typeEditable ? " editable" : ""}${typeActive ? " field-editing" : ""}" data-field="type">
            <span class="detail-label">节点类型</span>
            <div class="field-row">
                <span class="detail-value">${escapeText(TYPES[node.type].name)}</span>
                ${typeEditable && !typeActive ? pencil("type") : ""}
                <select class="field-control" id="inspectorTypeSelect">
                    <option value="S" ${node.type === "S" ? "selected" : ""}>串行节点</option>
                    <option value="P" ${node.type === "P" ? "selected" : ""}>并行节点</option>
                </select>
                ${typeActive ? confirmCancel() : ""}
            </div>
        </div>`);
        const ruleField = (definitions, selectedId, fallbackLabel) => {
            const active = inspectorEditingField === editKey(node.id, "expression");
            const options = [...(selectedId && !definitions.some((d) => d.ruleId === selectedId)
                ? [{ ruleId: selectedId, desc: fallbackLabel || selectedId }] : []), ...definitions];
            parts.push(`<div class="detail-field editable${active ? " field-editing" : ""}" data-field="expression">
                <span class="detail-label">引用规则</span>
                <div class="field-row">
                    <span class="detail-value">${escapeText(definitionReference(definitions, selectedId, fallbackLabel))}</span>
                    ${active ? "" : pencil("expression")}
                    <select class="field-control" id="inspectorDefinitionSelect">
                        ${options.map((d) => `<option value="${escapeText(d.ruleId)}" ${d.ruleId === selectedId ? "selected" : ""}>${escapeText(d.desc)} · ${escapeText(d.ruleId)}</option>`).join("")}
                    </select>
                    ${active ? confirmCancel() : ""}
                </div>
            </div>`);
        };
        const readonlyField = (label, value) => {
            parts.push(`<div class="detail-field">
                <span class="detail-label">${escapeText(label)}</span>
                <span class="detail-value">${escapeText(value)}</span>
            </div>`);
        };

        if (node.type === "A") {
            ruleField(ACTION_DEFINITIONS, node.expression, node.label);
            const next = relationChild(node, "next");
            if (next) readonlyField("流程", flowNodeDisplayName(next));
        } else if (node.type === "C") {
            ruleField(RULE_DEFINITIONS, node.expression, node.label);
        } else if (node.type === "J") {
            readonlyField("引用规则", judgeRuleReference(node));
        } else if (["S", "P"].includes(node.type)) {
            readonlyField("分支数", `${node.children.length} 个`);
        } else if (node.type === "D") {
            const branches = node.children.filter((child) => child.relation === "decision").length;
            readonlyField("分支数", `${branches} 个`);
            readonlyField("默认分支", relationChild(node, "default") ? "已设置" : "未设置");
        } else {
            const root = relationChild(node, "root");
            readonlyField("流程入口", root ? flowNodeDisplayName(root) : "未设置");
        }
        $("#nodeDetailFields").innerHTML = parts.join("");
        if (inspectorEditingField && inspectorEditingField.startsWith(`${node.id}:`)) {
            requestAnimationFrame(() => {
                const control = $("#nodeDetailFields .field-editing .field-control");
                control?.focus();
                if (control?.tagName === "INPUT") control.select();
            });
        }
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
        renderInspectorHeading(node);
        renderInspectorFields(node);
        $("#inspectorAddPanel").hidden = true;
        const operations = flowNodeOperations(node);
        const insertButton = node.type !== "ROOT"
            ? `<button class="flow-operation wide" type="button" data-insert-before>插入节点</button>`
            : "";
        $("#flowNodeActions").innerHTML = insertButton + operations.map((operation) => `
            <button class="flow-operation wide"
                    type="button" data-flow-relation="${operation.relation}">
                ${escapeText(operation.label)}
            </button>
        `).join("");
        const editButton = $("#editNodeButton");
        editButton.hidden = node.type !== "J";
        $("#inspectorViewActions").hidden = operations.length === 0 && editButton.hidden;
        $("#inspectorFooter").hidden = node.type === "ROOT";
        $("#deleteButton").disabled = !canDeleteFlowNode(node, found.parent);
        $("#deleteButton").title = $("#deleteButton").disabled
            ? "当前节点是所属结构的唯一必需分支，不能直接删除"
            : "";
    }

    function beginFieldEdit(field) {
        const found = findNode(selectedId);
        if (!found || !inspectorEditableType(found.node.type)) return;
        inspectorEditingField = editKey(found.node.id, field);
        renderInspectorHeading(found.node);
        renderInspectorFields(found.node);
    }

    function confirmFieldEdit() {
        const found = findNode(selectedId);
        if (!found || !inspectorEditingField) return;
        const node = found.node;
        const [, field] = inspectorEditingField.split(":");
        if (field === "alias") {
            const nameTarget = node.type === "J" ? judgeParts(node).rule : node;
            if (nameTarget) nameTarget.name = $("#inspectorAliasInput").value.trim();
        } else if (field === "expression" && ["A", "C"].includes(node.type)) {
            const definitions = node.type === "A" ? ACTION_DEFINITIONS : RULE_DEFINITIONS;
            const definition = definitions.find((candidate) => candidate.ruleId === $("#inspectorDefinitionSelect").value);
            if (definition) {
                node.label = definition.desc;
                node.expression = definition.ruleId;
            }
        } else if (field === "type" && ["S", "P"].includes(node.type)) {
            const newType = $("#inspectorTypeSelect").value;
            if (["S", "P"].includes(newType)) node.type = newType;
        }
        inspectorEditingField = null;
        render({ preserveView: true });
    }

    function cancelFieldEdit() {
        inspectorEditingField = null;
        const found = findNode(selectedId);
        if (found) { renderInspectorHeading(found.node); renderInspectorFields(found.node); }
    }

    function selectNode(id) {
        if (id !== selectedId) inspectorEditingField = null;
        selectedId = id;
        treeRoot.querySelectorAll(".node-shell").forEach((shell) => {
            shell.setAttribute("aria-selected", String(shell.dataset.nodeId === id));
        });
        updateInspector();
    }

    function confirmDiscardInspectorEdit() {
        inspectorEditingField = null;
        return true;
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

    function ruleNodeSubrules(node) {
        // 统一视角：原子规则视为只有一条子规则的列表，复合规则展开其直接子规则。
        if (node.type === "R") return [node];
        if (["L", "H"].includes(node.type)) return node.children.slice();
        return [];
    }

    function subruleRowLabel(child) {
        if (child.type === "R") {
            const definition = ruleDefinitionById(child.expression);
            return definition ? `${definition.desc} · ${definition.ruleId}` : (child.expression || "未设置");
        }
        return `${ruleNodeDisplayName(child)} · ${countNodes(child)} 个规则节点`;
    }

    function renderRuleEditorHeading(node) {
        const container = $("#ruleEditorHeading");
        if (!container) return;
        const active = ruleEditorEditing && ruleEditorDraftState;
        const name = active ? ruleEditorDraftState.name : (node.name?.trim() || "");
        const fallback = ruleNodeDisplayName(node);
        if (active) {
            container.innerHTML = `<strong>节点属性</strong>`;
        } else {
            container.innerHTML = `<strong>节点属性</strong>`;
        }
    }

    function renderRuleEditorSubrules() {
        const container = $("#ruleEditorSubrules");
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node) { container.innerHTML = ""; $("#ruleEditorAddRuleButton").hidden = true; renderRuleLogicField(); return; }
        const refs = ruleNodeSubrules(node);
        const canReorder = node.type === "L";
        container.innerHTML = refs.map((child, index) => {
            const lastIndex = refs.length - 1;
            const activeClass = child.id === highlightedRuleRowId ? " rule-row-active" : "";
            const moveButtons = canReorder ? `
                <button type="button" data-subrule-up="${index}" aria-label="上移" title="上移" ${index === 0 ? "disabled" : ""}>↑</button>
                <button type="button" data-subrule-down="${index}" aria-label="下移" title="下移" ${index === lastIndex ? "disabled" : ""}>↓</button>
            ` : "";
            if (child.type !== "R") {
                return `<div class="rule-add-row rule-subrule-static${activeClass}">
                    <span class="detail-value">${escapeText(subruleRowLabel(child))}</span>
                    ${moveButtons}
                </div>`;
            }
            return `<div class="rule-add-row${activeClass}">
                <select data-subrule-select="${index}">
                    ${RULE_DEFINITIONS.map((definition) => `<option value="${escapeText(definition.ruleId)}" ${definition.ruleId === child.expression ? "selected" : ""}>${escapeText(definition.desc)} · ${escapeText(definition.ruleId)}</option>`).join("")}
                </select>
                ${moveButtons}
            </div>`;
        }).join("");
        $("#ruleEditorAddRuleButton").hidden = !["L", "H"].includes(node.type);
        renderRuleLogicField();
    }

    let ruleLogicEditing = false;
    let highlightedRuleRowId = null;

    function renderRuleLogicField() {
        const container = $("#ruleEditorLogicField");
        if (!container) return;
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node || node.type === "H") { container.hidden = true; return; }
        container.hidden = false;
        // 原子节点与逻辑节点不可互换，节点类型仅在“逻辑与/逻辑或”之间切换
        if (node.type === "R") {
            ruleLogicEditing = false;
            container.className = "detail-field";
            container.innerHTML = `<span class="detail-label">节点类型</span>
                <div class="field-row"><span class="detail-value">原子节点</span></div>`;
            return;
        }
        const label = node.expression === "||" ? "逻辑或" : "逻辑与";
        const canEdit = !ruleEditorEditing; // 编辑规则(改子规则)期间不并行改类型
        container.className = "detail-field" + (canEdit ? " editable" : "") + (ruleLogicEditing ? " field-editing" : "");
        const pencil = `<button class="field-edit" type="button" data-rulelogic-edit aria-label="编辑" title="编辑">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20h4L18.5 9.5a2 2 0 0 0-2.83-2.83L5 17v3z"/><path d="M13.5 6.5l4 4"/></svg>
        </button>`;
        const confirmCancel = `<div class="field-edit-actions">
            <button class="field-confirm" type="button" data-rulelogic-confirm aria-label="确认" title="确认"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 13l4 4L19 7"/></svg></button>
            <button class="field-cancel" type="button" data-rulelogic-cancel aria-label="取消" title="取消"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg></button>
        </div>`;
        container.innerHTML = `<span class="detail-label">节点类型</span>
            <div class="field-row">
                <span class="detail-value">${label}</span>
                ${(canEdit && !ruleLogicEditing) ? pencil : ""}
                <select class="field-control" id="ruleLogicSelect">
                    <option value="&&" ${node.expression !== "||" ? "selected" : ""}>逻辑与</option>
                    <option value="||" ${node.expression === "||" ? "selected" : ""}>逻辑或</option>
                </select>
                ${ruleLogicEditing ? confirmCancel : ""}
            </div>`;
    }

    function beginRuleLogicEdit() {
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node || node.type !== "L" || ruleEditorEditing) return;
        ruleLogicEditing = true;
        renderRuleLogicField();
    }

    function confirmRuleLogicEdit() {
        const found = findRuleNode(ruleSelectedId);
        if (!found || found.node.type !== "L") { ruleLogicEditing = false; renderRuleLogicField(); return; }
        found.node.expression = $("#ruleLogicSelect")?.value === "||" ? "||" : "&&";
        ruleLogicEditing = false;
        renderRuleLogicField();
        renderRuleTree();
        render({ preserveView: true });
    }

    function cancelRuleLogicEdit() {
        ruleLogicEditing = false;
        renderRuleLogicField();
    }

    function updateRuleEditor() {
        ruleLogicEditing = false;
        highlightedRuleRowId = null;
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
        ruleEditorEditing = false;
        ruleEditorDraftNodeId = null;
        ruleEditorDraftState = null;
        renderRuleEditorHeading(node);
        const isHits = node.type === "H";
        $("#ruleReferenceField").hidden = !isHits;
        $("#ruleNodeExpression").value = isHits ? (node.expression || "") : "";
        $("#ruleNodeExpression").readOnly = true;
        $("#ruleEditorSubrulesField").hidden = false;
        renderRuleEditorSubrules();
        $("#ruleEditorEditActions")?.setAttribute("hidden", "");
        $("#ruleEditorSaveButton") && ($("#ruleEditorSaveButton").disabled = true);
        const toggleBtn = $("#ruleEditorEditToggleButton");
        if (toggleBtn) toggleBtn.textContent = "编辑规则";
        $("#ruleAddPanel").hidden = true;
        $("#ruleEditorViewActions")?.removeAttribute("hidden");
        $("#ruleEditorFooter").hidden = false;
        $("#ruleEditorDeleteButton").disabled = !canDeleteRule(found);
        $("#ruleEditorDeleteButton").title = canDeleteRule(found)
            ? ""
            : "组合规则至少需要保留两个子规则，不能直接删除";
    }

    function enterRuleEditorEditMode() {
        const found = findRuleNode(ruleSelectedId);
        if (!found) return;
        const { node } = found;
        ruleEditorEditing = true;
        ruleEditorDraftNodeId = node.id;
        ruleEditorDraftState = {
            name: node.name || "",
            refs: ruleNodeSubrules(node).map((child) => child.type === "R"
                ? { type: "R", ruleId: child.expression, node: child }
                : { type: child.type, node: child }),
            logic: node.type === "L" ? (node.expression === "||" ? "||" : "&&") : "&&",
            hitsExpr: node.type === "H" ? (node.expression || "") : ""
        };
        renderRuleEditorHeading(node);
        $("#ruleNodeExpression").readOnly = node.type !== "H";
        renderRuleEditorSubrules();
        $("#ruleEditorEditActions")?.setAttribute("hidden", "");
        const toggleBtn = $("#ruleEditorEditToggleButton");
        if (toggleBtn) toggleBtn.textContent = "完成编辑";
        $("#ruleEditorViewActions")?.removeAttribute("hidden");
        $("#ruleEditorFooter").hidden = false;
        $("#ruleAddPanel").hidden = true;
        updateRuleEditorDirtyState();
    }

    function readRuleEditorDraft() {
        const draft = ruleEditorDraftState;
        if (!draft) return;
        $("#ruleEditorSubrules").querySelectorAll("[data-subrule-select]").forEach((select) => {
            const index = Number(select.dataset.subruleSelect);
            if (draft.refs[index]?.type === "R") draft.refs[index].ruleId = select.value;
        });
        const aliasInput = $("#ruleAliasInput");
        if (aliasInput) draft.name = aliasInput.value.trim();
        if ($("#ruleNodeExpression").readOnly === false) draft.hitsExpr = $("#ruleNodeExpression").value.trim();
    }

    function ruleEditorHasUnsavedChanges() {
        if (!ruleEditorEditing || !ruleEditorDraftState) return false;
        readRuleEditorDraft();
        const found = findRuleNode(ruleEditorDraftNodeId);
        if (!found) return false;
        const node = found.node;
        const draft = ruleEditorDraftState;
        const originalRefs = ruleNodeSubrules(node).map((child) => child.type === "R" ? `R:${child.expression}` : `N:${child.id}`);
        const draftRefs = draft.refs.map((ref) => ref.type === "R" ? `R:${ref.ruleId}` : `N:${ref.node.id}`);
        if (draft.name !== (node.name || "")) return true;
        if (originalRefs.join("|") !== draftRefs.join("|")) return true;
        if (draft.refs.length >= 2 && node.type === "L" && draft.logic !== (node.expression === "||" ? "||" : "&&")) return true;
        if (node.type === "H" && draft.hitsExpr !== (node.expression || "")) return true;
        return false;
    }

    function updateRuleEditorDirtyState() {
        $("#ruleEditorSaveButton").disabled = !ruleEditorHasUnsavedChanges();
    }

    function addRuleEditorSubrule() {
        if (!ruleEditorDraftState) return;
        readRuleEditorDraft();
        ruleEditorDraftState.refs.push({ type: "R", ruleId: RULE_DEFINITIONS[0]?.ruleId || "", node: null });
        renderRuleEditorSubrules();
        updateRuleEditorDirtyState();
    }

    function currentEditingNode() {
        return findRuleNode(ruleSelectedId)?.node || null;
    }

    function syncDraftFromNode() { /* draft removed; no-op */ }

    function removeRuleEditorSubrule(index) {
        const node = currentEditingNode();
        if (!node || index < 0 || index >= node.children.length) return;
        if (node.type === "L" && node.children.length <= 2) {
            // 与/或 组合删到只剩 1 条：整体退化为幸存子规则
            const survivor = node.children[index === 0 ? 1 : 0];
            const grand = findRuleNode(node.id);
            const grandparent = grand ? grand.parent : currentRuleJudge();
            const idx = grandparent.children.findIndex((c) => c.id === node.id);
            if (idx >= 0) grandparent.children.splice(idx, 1, survivor);
            survivor.relation = "rule";
            ruleSelectedId = survivor.id;
            renderRuleTree();
            updateRuleEditor();
            render({ preserveView: true });
            return;
        }
        node.children.splice(index, 1);
        syncDraftFromNode(node);
        renderRuleTree();
        renderRuleEditorSubrules();
        render({ preserveView: true });
    }

    function moveRuleEditorSubrule(index, direction) {
        const node = currentEditingNode();
        if (!node) return;
        const target = index + direction;
        if (index < 0 || index >= node.children.length || target < 0 || target >= node.children.length) return;
        [node.children[index], node.children[target]] = [node.children[target], node.children[index]];
        highlightedRuleRowId = node.children[target].id;
        renderRuleTree();
        renderRuleEditorSubrules();
        render({ preserveView: true });
        requestAnimationFrame(() => {
            const same = direction < 0 ? `[data-subrule-up="${target}"]` : `[data-subrule-down="${target}"]`;
            const other = direction < 0 ? `[data-subrule-down="${target}"]` : `[data-subrule-up="${target}"]`;
            const sameBtn = document.querySelector(`#ruleEditorSubrules ${same}`);
            const otherBtn = document.querySelector(`#ruleEditorSubrules ${other}`);
            const pick = sameBtn && !sameBtn.disabled ? sameBtn : (otherBtn && !otherBtn.disabled ? otherBtn : null);
            pick?.focus();
        });
    }

    // ---- Composite builder (inline within L editor) ----
    let ruleCompositeRuleIds = [];

    function openCompositeBuilder() {
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node || !["L", "H"].includes(node.type)) return;
        ruleCompositeRuleIds = [RULE_DEFINITIONS[0]?.ruleId || ""];
        document.querySelector('input[name="ruleCompositeLogic"][value="&&"]').checked = true;
        renderCompositeRows();
        document.getElementById("ruleAddDialog").showModal();
    }

    function renderCompositeRows() {
        const container = $("#ruleCompositeRows");
        container.innerHTML = ruleCompositeRuleIds.map((ruleId, index) => `
            <div class="rule-add-row">
                <select data-composite-select="${index}">
                    ${RULE_DEFINITIONS.map((d) => `<option value="${escapeText(d.ruleId)}" ${d.ruleId === ruleId ? "selected" : ""}>${escapeText(d.desc)} · ${escapeText(d.ruleId)}</option>`).join("")}
                </select>
                <button type="button" data-composite-remove="${index}" aria-label="删除" ${ruleCompositeRuleIds.length <= 1 ? "disabled" : ""}>×</button>
            </div>
        `).join("");
        $("#ruleCompositeLogicField").hidden = ruleCompositeRuleIds.length < 2;
    }

    function appendCompositeRow() {
        const unused = RULE_DEFINITIONS.find((d) => !ruleCompositeRuleIds.includes(d.ruleId));
        ruleCompositeRuleIds.push(unused?.ruleId || RULE_DEFINITIONS[0]?.ruleId || "");
        renderCompositeRows();
    }

    function removeCompositeRow(index) {
        if (ruleCompositeRuleIds.length <= 1 || index < 0 || index >= ruleCompositeRuleIds.length) return;
        ruleCompositeRuleIds.splice(index, 1);
        renderCompositeRows();
    }

    function cancelCompositeBuilder() {
        ruleCompositeRuleIds = [];
        document.getElementById("ruleAddDialog").close();
    }

    function confirmCompositeBuilder() {
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node || ruleCompositeRuleIds.length < 1) return;
        $("#ruleCompositeRows").querySelectorAll("[data-composite-select]").forEach((select) => {
            ruleCompositeRuleIds[Number(select.dataset.compositeSelect)] = select.value;
        });
        let newNode;
        if (ruleCompositeRuleIds.length === 1) {
            newNode = createAtomicRule(ruleCompositeRuleIds[0]);
        } else {
            const logic = document.querySelector('input[name="ruleCompositeLogic"]:checked')?.value || "&&";
            const children = ruleCompositeRuleIds.map((ruleId) => createAtomicRule(ruleId));
            newNode = { id: `n${nextId++}`, type: "L", name: "", expression: logic, relation: "rule", children };
        }
        node.children.push(newNode);
        document.getElementById("ruleAddDialog").close();
        ruleCompositeRuleIds = [];
        renderRuleTree();
        renderRuleEditorSubrules();
        render({ preserveView: true });
    }

    function saveRuleEditorChanges({ refresh = true } = {}) {
        if (!ruleEditorHasUnsavedChanges()) return false;
        const found = findRuleNode(ruleEditorDraftNodeId);
        if (!found) return false;
        readRuleEditorDraft();
        const draft = ruleEditorDraftState;
        const node = found.node;
        const buildChild = (ref) => {
            if (ref.type !== "R") return ref.node;
            if (!ruleDefinitionById(ref.ruleId)) return null;
            if (!ref.node) return createAtomicRule(ref.ruleId);
            return {
                ...ref.node,
                expression: ref.ruleId,
                children: []
            };
        };
        let replacement;
        if (node.type === "H") {
            const children = draft.refs.map(buildChild);
            if (children.some((child) => !child)) return false;
            replacement = {
                ...node,
                name: draft.name,
                expression: draft.hitsExpr || node.expression,
                children
            };
        } else if (node.type === "R" && draft.refs.length >= 2) {
            const children = draft.refs.map(buildChild);
            if (children.some((child) => !child)) return false;
            children[0].name = draft.name;
            children.forEach((child) => { child.relation = "rule"; });
            replacement = {
                id: `n${nextId++}`,
                type: "L",
                name: "",
                expression: draft.logic,
                relation: node.relation,
                children
            };
        } else if (draft.refs.length === 1) {
            replacement = buildChild(draft.refs[0]);
            if (!replacement) return false;
            replacement.relation = node.relation;
            if (node.type === "R") {
                replacement.id = node.id;
                replacement.name = draft.name;
            }
        } else {
            const children = draft.refs.map(buildChild);
            if (children.some((child) => !child)) return false;
            replacement = {
                ...node,
                type: "L",
                name: draft.name,
                expression: draft.logic,
                children
            };
        }
        if (found.parent) {
            const idx = found.parent.children.findIndex((child) => child.id === node.id);
            if (idx >= 0) found.parent.children.splice(idx, 1, replacement);
        }
        ruleSelectedId = replacement.id;
        ruleEditorEditing = false;
        ruleEditorDraftState = null;
        if (refresh) refreshRuleAfterEdit(findRuleNode(ruleSelectedId));
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

    let ruleDialogSnapshot = null;
    let ruleDialogCommitted = false;

    function openRuleDialog(judgeId, { edit = false } = {}) {
        const found = findNode(judgeId);
        if (!found || found.node.type !== "J") return;
        ruleJudgeId = judgeId;
        ruleDialogSnapshot = JSON.parse(JSON.stringify(found.node.children));
        ruleDialogCommitted = false;
        ruleSelectedId = judgeParts(found.node).rule?.id || null;
        closeRuleDialogAfterEdit = edit;
        hideRulePopover();
        renderRuleDialog();
        if (!ruleDialog.open) ruleDialog.showModal();
        if (edit) enterRuleEditorEditMode();
    }

    function hideRulePopover() {
        $("#ruleAddPanel").hidden = true;
    }

    function positionRulePopover() {}

    function ruleDefinitionById(ruleId) {
        return RULE_DEFINITIONS.find((definition) => definition.ruleId === ruleId) || null;
    }

    function actionDefinitionById(ruleId) {
        return ACTION_DEFINITIONS.find((definition) => definition.ruleId === ruleId) || null;
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
        if (mode === "root") {
            $("#ruleEditorEmpty").hidden = true;
            $("#ruleEditorPanel").hidden = false;
            $("#ruleEditorHeading").innerHTML = "";
            $("#ruleEditorSubrulesField").hidden = true;
            $("#ruleEditorLogicField").hidden = true;
            $("#ruleReferenceField").hidden = true;
        }
        $("#ruleAddTitle").textContent = mode === "root"
            ? "配置根规则"
            : (mode === "before" ? "上方插入规则" : "下方插入规则");

        ruleAddRuleIds = [RULE_DEFINITIONS[0]?.ruleId || ""];
        document.querySelector('input[name="ruleAddLogic"][value="&&"]').checked = true;
        $("#ruleEditorViewActions")?.setAttribute("hidden", "");
        $("#ruleEditorEditActions")?.setAttribute("hidden", "");
        $("#ruleEditorFooter").hidden = true;
        $("#ruleAddPanel").hidden = false;
        renderRuleAddRows(0);
    }

    function cancelRuleAdd() {
        ruleAddParentId = null;
        ruleAddAnchorId = null;
        ruleAddRuleIds = [];
        $("#ruleAddPanel").hidden = true;
        updateRuleEditor();
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

    async function deleteSelectedRule() {
        const found = findRuleNode(ruleSelectedId);
        if (!canDeleteRule(found)) return;
        const descendants = countNodes(found.node) - 1;
        const name = ruleNodeDisplayName(found.node);
        const message = descendants ? `删除「${name}」及其 ${descendants} 个子规则？` : `删除「${name}」？`;
        if (!(await openConfirm(message, { title: "确认删除", confirmLabel: "删除" }))) return;
        hideRulePopover();
        const parent = found.parent;
        parent.children = parent.children.filter((child) => child.id !== found.node.id);
        if (parent.type === "L" && parent.children.length === 1) {
            // 与/或 组合只剩一条子规则时，自动退化为该子规则
            const survivor = parent.children[0];
            const grand = findRuleNode(parent.id);
            const grandparent = grand ? grand.parent : currentRuleJudge();
            const index = grandparent.children.findIndex((child) => child.id === parent.id);
            if (index >= 0) grandparent.children.splice(index, 1, survivor);
            survivor.relation = "rule";
            ruleSelectedId = survivor.id;
        } else {
            ruleSelectedId = ["L", "H"].includes(parent.type) ? parent.id : null;
        }
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
        const placement = $("#newNodePlacement");
        const anchor = $("#newNodeAnchor");
        const showPosition = children.length > 0;
        placementField.hidden = !showPosition;
        placement.value = "last";
        anchor.innerHTML = children
            .map((child, index) => `
                <option value="${escapeText(child.id)}">
                    ${index + 1}. ${escapeText(flowNodeDisplayName(child))}
                </option>`)
            .join("");
        updatePlacementVisibility();
    }

    function updatePlacementVisibility() {
        const value = $("#newNodePlacement").value;
        $("#newNodeAnchorField").hidden = !(value === "before" || value === "after");
    }

    function updateNewNodeFields() {
        populateNewNodeDefinitions();
    }

    function openInsertPanel() {
        const found = findNode(selectedId);
        if (!found || found.node.type === "ROOT") return;
        insertBeforeMode = true;
        addRelation = null;
        $("#inspectorAddTitle").textContent = "插入节点";
        const restricted = found.node.relation === "decision";
        const types = restricted ? ["J"] : FLOW_TYPES;
        populateTypeSelect($("#newNodeType"), types);
        $("#newNodeType").value = restricted ? "J" : (types.includes("A") ? "A" : types[0]);
        updateNewNodeFields();
        $("#newNodePlacementField").hidden = true;
        $("#newNodeAnchorField").hidden = true;
        $("#inspectorViewActions").hidden = true;
        $("#inspectorFooter").hidden = true;
        $("#inspectorAddPanel").hidden = false;
        requestAnimationFrame(() => { $("#newNodeType").focus(); });
    }

    function openAddDialog(relation) {
        const found = findNode(selectedId);
        if (!found) return;
        const operation = flowNodeOperations(found.node).find((item) => item.relation === relation);
        if (!operation) return;
        addRelation = relation;
        $("#inspectorAddTitle").textContent = operation.dialogTitle;
        populateNewNodeTypes();
        populateNewNodeAnchors(found.node);
        $("#inspectorViewActions").hidden = true;
        $("#inspectorFooter").hidden = true;
        $("#inspectorAddPanel").hidden = false;
        requestAnimationFrame(() => { $("#newNodeType").focus(); });
    }

    function closeAddPanel() {
        addRelation = null;
        insertBeforeMode = false;
        $("#inspectorAddPanel").hidden = true;
        updateInspector();
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
        const placement = $("#newNodePlacementField").hidden ? "last" : $("#newNodePlacement").value;
        const siblingIndexes = parent.children
            .map((child, index) => ({ child, index }))
            .filter((entry) => entry.child.relation === addRelation)
            .map((entry) => entry.index);
        if (placement === "first" && siblingIndexes.length) {
            parent.children.splice(siblingIndexes[0], 0, node);
        } else if ((placement === "before" || placement === "after")) {
            const anchorId = $("#newNodeAnchor").value;
            const anchorIndex = parent.children.findIndex((child) =>
                child.id === anchorId && child.relation === addRelation);
            if (anchorIndex >= 0) {
                parent.children.splice(placement === "before" ? anchorIndex : anchorIndex + 1, 0, node);
            } else {
                parent.children.push(node);
            }
        } else if (placement === "last" && siblingIndexes.length) {
            parent.children.splice(siblingIndexes[siblingIndexes.length - 1] + 1, 0, node);
        } else {
            parent.children.push(node);
        }
        selectedId = node.id;
        addRelation = null;
        $("#inspectorAddPanel").hidden = true;
        fitMode = false;
        render({ preserveView: true });
        return node;
    }

    function insertNodeBefore() {
        const found = findNode(selectedId);
        if (!found || !found.parent) return;
        const { node: target, parent } = found;
        const type = $("#newNodeType").value;
        const definitionId = $("#newNodeDefinition").value;
        const definition = newNodeDefinitions(type).find((c) => c.ruleId === definitionId);
        if (["A", "J"].includes(type) && !definition) return;
        const childRelation = { A: "next", S: "branch", P: "branch", C: "action", J: "action", D: "default" }[type];
        if (!childRelation) return;
        const node = { id: `n${nextId++}`, type, relation: target.relation, children: [] };
        if (type === "A") {
            node.label = definition.desc;
            node.expression = definition.ruleId;
        } else if (type === "J") {
            const rule = createAtomicRule(definition.ruleId);
            if (!rule) return;
            node.children.push(rule);
        }
        const index = parent.children.indexOf(target);
        if (index < 0) return;
        target.relation = childRelation;
        node.children.push(target);
        parent.children.splice(index, 1, node);
        selectedId = node.id;
        insertBeforeMode = false;
        $("#inspectorAddPanel").hidden = true;
        fitMode = false;
        render({ preserveView: true });
        return node;
    }

    async function deleteSelected() {
        const found = findNode(selectedId);
        if (!found || !canDeleteFlowNode(found.node, found.parent)) return;
        const descendants = countFlowNodes(found.node) - 1;
        const nodeName = flowNodeDisplayName(found.node);
        const nodeId = found.node.id;
        const message = descendants
            ? `确定删除「${nodeName}」及其 ${descendants} 个流程子节点吗？`
            : `确定删除「${nodeName}」吗？`;
        if (!(await openConfirm(message, { title: "确认删除", confirmLabel: "删除" }))) return;
        const current = findNode(nodeId);
        if (!current || !canDeleteFlowNode(current.node, current.parent)) return;
        current.parent.children = current.parent.children.filter((child) => child.id !== current.node.id);
        selectedId = current.parent.id;
        render({ preserveView: true });
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
        const zoomInput = $("#zoomValue");
        if (document.activeElement !== zoomInput) zoomInput.value = `${Math.round(scale * 100)}%`;
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
        scale = Math.min(1.12, Math.max(.68, Math.min((viewport.clientWidth - 46) / width, (viewport.clientHeight - 46) / height)));
        panX = Math.max(12, (viewport.clientWidth - width * scale) / 2);
        panY = Math.max(12, (viewport.clientHeight - height * scale) / 2);
        fitMode = true;
        applyTransform();
    }

    treeRoot.addEventListener("click", (event) => {
        const shell = event.target.closest(".node-shell");
        if (!shell) return;
        const nextId = shell.dataset.nodeId;
        if (nextId !== selectedId && !confirmDiscardInspectorEdit()) return;
        selectNode(nextId);
        const action = event.target.closest("[data-action]")?.dataset.action;
        if (action === "collapse") toggleCollapse();
    });
    treeRoot.addEventListener("dblclick", (event) => {
        const shell = event.target.closest(".node-shell");
        if (!shell) return;
        const node = findNode(shell.dataset.nodeId)?.node;
        if (node?.type === "J") openRuleDialog(node.id);
    });

    treeRoot.addEventListener("keydown", (event) => {
        if ((event.key === "Enter" || event.key === " ") && event.target.matches(".node-shell")) {
            event.preventDefault();
            const nextId = event.target.dataset.nodeId;
            if (nextId !== selectedId && !confirmDiscardInspectorEdit()) return;
            selectNode(nextId);
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
        if (event.target.closest("[data-insert-before]")) { openInsertPanel(); return; }
        const relation = event.target.closest("[data-flow-relation]")?.dataset.flowRelation;
        if (relation) openAddDialog(relation);
    });
    $("#zoomOutButton").addEventListener("click", () => setZoom(scale - .1));
    $("#zoomInButton").addEventListener("click", () => setZoom(scale + .1));
    const zoomInput = $("#zoomValue");
    const revertZoomInput = () => { zoomInput.value = `${Math.round(scale * 100)}%`; };
    const applyZoomInput = () => {
        const pct = parseFloat(String(zoomInput.value).replace(/[^0-9.]/g, ""));
        if (Number.isFinite(pct) && pct > 0) setZoom(pct / 100);
        revertZoomInput();
    };
    zoomInput.addEventListener("focus", () => zoomInput.select());
    zoomInput.addEventListener("keydown", (event) => {
        if (event.key === "Enter") { event.preventDefault(); applyZoomInput(); zoomInput.blur(); }
        else if (event.key === "Escape") { event.preventDefault(); revertZoomInput(); zoomInput.blur(); }
    });
    zoomInput.addEventListener("blur", applyZoomInput);
    $("#fitButton").addEventListener("click", fitTree);
    $("#collapseAllButton").addEventListener("click", () => {
        walk(tree, (node) => { if (node.type !== "J" && node.children.length) node.collapsed = true; });
        render({ preserveView: true });
    });
    $("#expandAllButton").addEventListener("click", () => {
        walk(tree, (node) => { node.collapsed = false; });
        render({ preserveView: true });
    });
    function setInspectorCollapsed(collapsed) {
        document.querySelector(".workspace").classList.toggle("inspector-collapsed", collapsed);
        $("#inspectorExpand").hidden = !collapsed;
        if (fitMode) requestAnimationFrame(fitTree);
    }
    $("#inspectorCollapse").addEventListener("click", () => setInspectorCollapsed(true));
    $("#inspectorExpand").addEventListener("click", () => setInspectorCollapsed(false));
    $("#deleteButton").addEventListener("click", deleteSelected);
    $("#editNodeButton").addEventListener("click", () => {
        const found = findNode(selectedId);
        if (found?.node.type === "J") openRuleDialog(found.node.id);
    });
    const inspectorFieldClick = (event) => {
        const editBtn = event.target.closest("[data-field-edit]");
        if (editBtn) { beginFieldEdit(editBtn.dataset.fieldEdit); return; }
        if (event.target.closest("[data-field-confirm]")) { confirmFieldEdit(); return; }
        if (event.target.closest("[data-field-cancel]")) { cancelFieldEdit(); return; }
    };
    const inspectorFieldKeydown = (event) => {
        if (!inspectorEditingField) return;
        if (event.key === "Enter" && event.target.matches("input")) { event.preventDefault(); confirmFieldEdit(); }
        else if (event.key === "Escape") { event.preventDefault(); cancelFieldEdit(); }
    };
    $("#nodeDetailFields").addEventListener("click", inspectorFieldClick);
    $("#nodeDetailFields").addEventListener("keydown", inspectorFieldKeydown);
    $("#inspectorHeading").addEventListener("click", inspectorFieldClick);
    $("#inspectorHeading").addEventListener("keydown", inspectorFieldKeydown);

    $("#ruleDialogCloseButton").addEventListener("click", () => {
        ruleDialogCommitted = true;
        ruleDialog.close();
    });
    $("#ruleDialogCancelButton").addEventListener("click", () => {
        ruleDialogCommitted = false;
        ruleDialog.close();
    });
    $("#ruleEditorDeleteButton").addEventListener("click", () => {
        if (!confirmDiscardRuleEditorChanges()) return;
        deleteSelectedRule();
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
    $("#ruleEditorAddRuleButton").addEventListener("click", openCompositeBuilder);
    $("#ruleCompositeAddRow").addEventListener("click", appendCompositeRow);
    $("#ruleCompositeRows").addEventListener("click", (event) => {
        const button = event.target.closest("[data-composite-remove]");
        if (button) removeCompositeRow(Number(button.dataset.compositeRemove));
    });
    $("#ruleCompositeCancelButton")?.addEventListener("click", cancelCompositeBuilder);
    document.getElementById("ruleAddDialog").addEventListener("close", () => { ruleCompositeRuleIds = []; });
    $("#ruleCompositeConfirmButton").addEventListener("click", confirmCompositeBuilder);
    $("#ruleEditorSubrules").addEventListener("change", (event) => {
        const select = event.target.closest("[data-subrule-select]");
        if (!select) return;
        const node = currentEditingNode();
        if (!node) return;
        const idx = Number(select.dataset.subruleSelect);
        const child = node.children[idx];
        if (child && child.type === "R") {
            child.expression = select.value;
            syncDraftFromNode(node);
            renderRuleTree();
            render({ preserveView: true });
        }
    });
    $("#ruleEditorSubrules").addEventListener("click", (event) => {
        const removeBtn = event.target.closest("[data-subrule-remove]");
        if (removeBtn) { removeRuleEditorSubrule(Number(removeBtn.dataset.subruleRemove)); return; }
        const upBtn = event.target.closest("[data-subrule-up]");
        if (upBtn) { moveRuleEditorSubrule(Number(upBtn.dataset.subruleUp), -1); return; }
        const downBtn = event.target.closest("[data-subrule-down]");
        if (downBtn) { moveRuleEditorSubrule(Number(downBtn.dataset.subruleDown), 1); return; }
    });
    $("#ruleEditorLogicField").addEventListener("click", (event) => {
        if (event.target.closest("[data-rulelogic-edit]")) { beginRuleLogicEdit(); return; }
        if (event.target.closest("[data-rulelogic-confirm]")) { confirmRuleLogicEdit(); return; }
        if (event.target.closest("[data-rulelogic-cancel]")) { cancelRuleLogicEdit(); return; }
    });
    $("#ruleEditorCancelButton")?.addEventListener("click", finishRuleEditorCancel);
    $("#ruleEditorSaveButton")?.addEventListener("click", finishRuleEditorSave);
    $("#ruleEditorHeading").addEventListener("input", (event) => {
        if (event.target.id === "ruleAliasInput") updateRuleEditorDirtyState();
    });
    $("#ruleNodeExpression").addEventListener("input", updateRuleEditorDirtyState);
    ruleDialog.addEventListener("cancel", () => { ruleDialogCommitted = false; });
    ruleDialog.addEventListener("close", () => {
        if (!ruleDialogCommitted && ruleDialogSnapshot && ruleJudgeId) {
            const found = findNode(ruleJudgeId);
            if (found) found.node.children = ruleDialogSnapshot;
        }
        ruleDialogSnapshot = null;
        ruleDialogCommitted = false;
        ruleEditorEditing = false;
        ruleEditorDraftNodeId = null;
        ruleEditorDraftState = null;
        closeRuleDialogAfterEdit = false;
        ruleJudgeId = null;
        ruleSelectedId = null;
        hideRulePopover();
        render({ preserveView: true });
    });

    document.addEventListener("pointerdown", (event) => {
        if (event.target.closest(".node-shell")) return;
        document.activeElement?.closest?.(".node-shell")?.blur();
    }, true);

    $("#newNodeType").addEventListener("change", updateNewNodeFields);
    $("#newNodePlacement").addEventListener("change", updatePlacementVisibility);
    $("#confirmAddButton").addEventListener("click", () => {
        const node = insertBeforeMode ? insertNodeBefore() : addNode();
        if (node?.type === "J") requestAnimationFrame(() => openRuleDialog(node.id));
    });
    $("#newNodeCancelButton").addEventListener("click", closeAddPanel);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#inspectorAddPanel").hidden) closeAddPanel();
        const editing = event.target.matches("input, select, textarea");
        if (!editing && ruleDialog.open && (event.key === "Delete" || event.key === "Backspace")) {
            deleteSelectedRule();
        }
        if (!editing && event.key === "0") fitTree();
    });

    new ResizeObserver(() => { if (fitMode) fitTree(); }).observe(viewport);
    render({ preserveView: false });
})();
