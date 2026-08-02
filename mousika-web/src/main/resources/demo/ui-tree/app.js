(() => {
    "use strict";

    // 画布页必须依附一条规则流程。通过 HTTP 访问且无 flowId（bench 基准除外）时，
    // 回到场景列表，避免正式服务里裸开画布展示内置演示假数据造成困惑。
    // flowId 取自路径 /flow/{id}（正式路由）或查询串 ?flowId=（向后兼容）。
    function resolveFlowId() {
        const m = location.pathname.match(/\/flow\/(\d+)/);
        if (m) return m[1];
        return new URLSearchParams(location.search).get("flowId");
    }
    {
        const params = new URLSearchParams(location.search);
        if (location.protocol.startsWith("http") && !resolveFlowId() && !params.has("bench")) {
            location.replace("/");
            return;
        }
    }

    // 语义模型核心（唯一事实来源 = 后端 TreeNode JSON）。编辑器只在其上做视图与瞬时状态。
    const T = window.MousikaTree;
    const { childEdges, childNodes, make } = T;

    const TYPES = {
        T: { name: "根节点", kind: "root", short: "开始", help: "规则树入口，仅承担根节点定位。" },
        S: { name: "串行节点", kind: "structure", short: "串", help: "子节点按顺序执行，顺序由树中从左到右的位置表达。" },
        P: { name: "并行节点", kind: "structure", short: "并", help: "多个子节点并发执行；结构节点本身不承载业务结果。" },
        D: { name: "分支节点", kind: "structure", short: "分", help: "按顺序检查各条件，多选一并在首个命中后停止，最后可设置默认分支。" },
        J: { name: "条件节点", kind: "structure", short: "条件", help: "引用一棵可递归嵌套的纯规则树，并连接可选的后续流程。" },
        L: { name: "逻辑", kind: "structure", short: "与", help: "使用“与”或“或”组合两个及以上纯规则子节点。" },
        H: { name: "命中数", kind: "structure", short: "H", help: "表达 hits(min,max,...)；例如至少命中 2 项。" },
        R: { name: "规则", kind: "condition", short: "R", help: "只参与规则匹配的原子表达式，不连接业务动作。" },
        C: { name: "条件节点", kind: "condition", short: "条件", help: "引用一条后台判断条件，可连接一个可选的后续流程。" },
        A: { name: "动作节点", kind: "action", short: "动作", help: "引用一条后台执行动作，执行后可连接一个可选的下一步。" },
        PH: { name: "待配置", kind: "placeholder", short: "待配置", help: "待配置的占位节点，点击选择其类型与引用规则；存在占位时不能保存/生效。" }
    };

    // 规则定义：默认演示假数据，接入后端后由 /api/rules 覆盖（见文件末尾接线层）。
    // expr 只保存稳定的 ruleId 引用；desc 供展示。动作与条件同源于规则池。
    let RULE_DEFINITIONS = Array.from({ length: 12 }, (_, index) => ({
        ruleId: `c${index + 1}`, desc: `业务判断条件${index + 1}`, useType: 0
    }));
    let ACTION_DEFINITIONS = Array.from({ length: 13 }, (_, index) => ({
        ruleId: `a${index + 1}`, desc: `业务操作${index + 1}`, useType: 0
    }));
    let RULE_DEFINITION_BY_ID = new Map(RULE_DEFINITIONS.map((d) => [d.ruleId, d]));
    let ACTION_DEFINITION_BY_ID = new Map(ACTION_DEFINITIONS.map((d) => [d.ruleId, d]));

    // 接入后端时用真实 RuleDefinition 列表覆盖演示数据，并按 ruleKind 拆分：
    // 判断条件(condition) 供判断/条件节点引用；执行动作(action) 供动作节点引用。
    function applyRuleDefinitions(list) {
        const toDef = (r) => ({
            ruleId: String(r.id ?? r.ruleId),
            desc: r.name || r.description || String(r.id ?? r.ruleId),
            useType: r.useType ?? 0,
            ruleKind: r.ruleKind === "action" ? "action" : "condition"
        });
        const defs = (list || []).map(toDef);
        RULE_DEFINITIONS = defs.filter((d) => d.ruleKind !== "action");
        ACTION_DEFINITIONS = defs.filter((d) => d.ruleKind === "action");
        RULE_DEFINITION_BY_ID = new Map(RULE_DEFINITIONS.map((d) => [d.ruleId, d]));
        ACTION_DEFINITION_BY_ID = new Map(ACTION_DEFINITIONS.map((d) => [d.ruleId, d]));
    }

    // 与 img/ui-tree.png 逐节点对应，用 TreeNode 语义模型重建。
    const sampleTree = () => {
        const root = make.root();
        const s = make.serial();
        root.next = s;

        const p = make.parallel();
        ["a11", "a12", "a13"].forEach((id) => p.branches.push(make.action(id)));
        s.branches.push(p);

        const d = make.decision();

        const l1 = make.logic("||"); l1.name = "业务复合规则1";
        const l1b = make.logic("&&"); l1b.rules.push(make.atom("c2"), make.atom("c3"));
        l1.rules.push(make.atom("c1"), l1b);
        const j8 = make.judge(l1);
        const s14 = make.serial();
        const d15 = make.decision();
        const j16 = make.judge(make.atom("c1")); j16.action = make.action("a1");
        const l18 = make.logic("&&"); l18.name = "业务复合规则2"; l18.rules.push(make.atom("c2"), make.atom("c3"));
        const j18 = make.judge(l18); j18.action = make.action("a3");
        d15.branches.push(j16, j18);
        s14.branches.push(d15);
        const j23 = make.judge(make.atom("c4")); j23.action = make.action("a4");
        s14.branches.push(j23);
        j8.action = s14;

        const j25 = make.judge(make.atom("c5"));
        const d26 = make.decision();
        const j27 = make.judge(make.atom("c6")); j27.action = make.action("a6");
        const j29 = make.judge(make.atom("c7")); j29.action = make.action("a7");
        d26.branches.push(j27, j29);
        d26.action = make.action("a5");
        j25.action = d26;

        const s32 = make.serial();
        const j33 = make.judge(make.atom("c8")); j33.action = make.action("a8");
        const j35 = make.judge(make.atom("c9")); j35.action = make.action("a9");
        s32.branches.push(j33, j35);

        d.branches.push(j8, j25);
        d.action = s32;
        s.branches.push(d);
        return root;
    };

    // ---- 语义树 + 旁路编辑器状态（选中/折叠/临时 id 不进语义树）----
    // 真实流程（带 flowId）初始为空白根，等待后端加载替换，避免把内置演示假数据当成真实流程闪现；
    // 独立演示 / bench 才用 sampleTree。
    const emptyTree = () => ({ type: "T", expr: "", next: null });
    let tree = resolveFlowId() ? emptyTree() : sampleTree();
    let flowLoadFailed = false;
    let idSeq = 0;
    const idMap = new WeakMap();
    const collapsedIds = new Set();
    function idOf(node) {
        let id = idMap.get(node);
        if (!id) { id = `n${++idSeq}`; idMap.set(node, id); }
        return id;
    }
    function isCollapsed(node) { return collapsedIds.has(idOf(node)); }
    function setCollapsed(node, value) {
        const id = idOf(node);
        if (value) collapsedIds.add(id); else collapsedIds.delete(id);
    }

    let selectedId = null;
    let docStatus = "draft";
    let inspectorEditingField = null;
    let insertBeforeMode = false;
    let scale = 1;
    let panX = 0;
    let panY = 0;
    let fitMode = true;
    let drag = null;
    let ruleJudgeId = null;
    let ruleSelectedId = null;
    let ruleAddRuleIds = [];
    // 后端接线：当前打开的 flow 元数据（null=独立演示，无法保存）。
    let flowMeta = null;
    let savingFlow = false;
    let dirty = false;

    const FLOW_TYPES = ["A", "S", "P", "D", "J"];
    // 各真实递归边允许的可创建节点类型。
    const RELATIONS = {
        next: { types: FLOW_TYPES },
        action: { types: FLOW_TYPES },
        branch: { types: FLOW_TYPES },
        decision: { types: ["J"] },
        default: { types: FLOW_TYPES }
    };

    const TREE_LIMITS = { maxDepth: 128, maxNodes: 2000 };

    const $ = (selector) => document.querySelector(selector);
    const treeRoot = $("#treeRoot");
    const viewport = $("#treeViewport");
    const stage = $("#treeStage");
    const confirmDialog = $("#confirmDialog");
    const ruleDialog = $("#ruleDialog");
    const ruleTreeRoot = $("#ruleTreeRoot");

    // ---- 遍历与定位（基于 childEdges，携带 relation 上下文）----
    function walk(node, visitor, parent = null, relation = null) {
        if (visitor(node, parent, relation) === false) return false;
        for (const edge of childEdges(node)) {
            if (walk(edge.node, visitor, node, edge.relation) === false) return false;
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
        walk(tree, (node, parent, relation) => {
            if (idOf(node) === id) { found = { node, parent, relation }; return false; }
            return true;
        });
        return found;
    }

    // J 的规则子树与命中流程拆分。
    function jrule(node) { return node.type === "J" ? (node.rule || null) : null; }
    function jflows(node) {
        return node.type === "J"
            ? childEdges(node).filter((edge) => edge.relation === "action").map((edge) => edge.node)
            : [];
    }

    function countNodes(node) {
        return 1 + childNodes(node).reduce((sum, child) => sum + countNodes(child), 0);
    }

    function countFlowNodes(node) {
        if (node.type === "J") {
            return 1 + jflows(node).reduce((sum, child) => sum + countFlowNodes(child), 0);
        }
        return 1 + childNodes(node).reduce((sum, child) => sum + countFlowNodes(child), 0);
    }

    // ---- 名称解析：流程节点用 label（可选展示名），规则节点用 name ----
    function flowNodeName(node) {
        if (node.type === "J") return jrule(node)?.name?.trim() || "";
        if (["R", "L", "H"].includes(node.type)) return node.name?.trim() || "";
        return node.label?.trim() || "";
    }

    function ruleDisplayName(node) {
        const rule = jrule(node);
        if (!rule) return "未配置规则";
        return ruleNodeDisplayName(rule);
    }

    function ruleNodeDisplayName(node) {
        const alias = node.name?.trim();
        if (alias) return alias;
        if (node.type === "R") {
            return ruleDefinitionById(node.expr)?.desc || "未命名规则";
        }
        return ["L", "H"].includes(node.type) ? "复合规则" : TYPES[node.type]?.name || "未命名规则";
    }

    function isStructuralFlowType(type) {
        return ["T", "S", "P", "D"].includes(type);
    }

    function flowNodeDisplayName(node) {
        if (node.type === "T") return "开始";
        const name = flowNodeName(node);
        if (name) return name;
        if (isStructuralFlowType(node.type)) return TYPES[node.type].name;
        if (node.type === "J") return ruleDisplayName(node);
        if (node.type === "A") {
            return actionDefinitionById(node.expr)?.desc || TYPES[node.type].name;
        }
        if (node.type === "C") {
            return ruleDefinitionById(node.expr)?.desc || TYPES[node.type].name;
        }
        return TYPES[node.type]?.name || "未命名节点";
    }

    // 主画布状态一次遍历完成，避免每次 render 后再分别扫描流程数、折叠数和弹窗规则数。
    function canvasStats(root) {
        let flowNodes = 0;
        let hiddenNodes = 0;
        let modalRuleNodes = 0;
        const stack = [{ node: root, hidden: false }];
        while (stack.length) {
            const { node, hidden } = stack.pop();
            if (!node) continue;
            flowNodes++;
            if (hidden) hiddenNodes++;
            if (node.type === "J" && jrule(node)) modalRuleNodes += countNodes(jrule(node));
            const children = node.type === "J" ? jflows(node) : childNodes(node);
            const hideChildren = hidden || (node.type !== "J" && isCollapsed(node) && children.length > 0);
            for (const child of children) stack.push({ node: child, hidden: hideChildren });
        }
        return { flowNodes, hiddenNodes, modalRuleNodes };
    }

    // ---- 迭代式安全预算校验（覆盖 Flow 与内嵌 Rule）----
    function inspectTree(root) {
        const seen = new Set();
        let nodes = 0, maxDepth = 0, deepest = null, dup = null;
        const stack = [[root, 1]];
        while (stack.length) {
            const [node, depth] = stack.pop();
            if (!node) continue;
            if (seen.has(node)) { dup = node; continue; }
            seen.add(node);
            nodes++;
            if (depth > maxDepth) { maxDepth = depth; deepest = node; }
            for (const child of childNodes(node)) stack.push([child, depth + 1]);
        }
        return { nodes, depth: maxDepth, deepest, dup };
    }

    function assertTreeWithinLimits(root, label = "规则树") {
        const { nodes, depth, deepest, dup } = inspectTree(root);
        if (dup) throw new Error(`${label}存在重复或循环引用的节点（id=${dup ? idOf(dup) : "?"}），已拒绝加载`);
        if (depth > TREE_LIMITS.maxDepth) {
            throw new Error(`${label}嵌套深度 ${depth} 超过上限 ${TREE_LIMITS.maxDepth}（最深节点 id=${deepest ? idOf(deepest) : "?"}）`);
        }
        if (nodes > TREE_LIMITS.maxNodes) {
            throw new Error(`${label}节点总数 ${nodes} 超过上限 ${TREE_LIMITS.maxNodes}`);
        }
        return { nodes, depth };
    }

    // 导入边界：校验通过才整体替换。root 可传语义树或后端 TreeNode JSON。
    function loadTree(root, { bypassLimits = false } = {}) {
        const semantic = (root && typeof root === "object" && root.type) ? root : T.deserialize(root);
        if (!bypassLimits) assertTreeWithinLimits(semantic, "导入的树");
        tree = semantic;
        collapsedIds.clear();
        selectedId = null;
        ruleSelectedId = null;
        ruleJudgeId = null;
        inspectorEditingField = null;
        docStatus = "draft";
        dirty = false;
        updateDocStatus();
        render({ preserveView: false });
    }

    function depthOf(targetId) {
        const stack = [[tree, 1]];
        while (stack.length) {
            const [node, depth] = stack.pop();
            if (idOf(node) === targetId) return depth;
            for (const child of childNodes(node)) stack.push([child, depth + 1]);
        }
        return -1;
    }

    function limitBlockReason(parentNode, addedRoot) {
        const cur = inspectTree(tree);
        const add = inspectTree(addedRoot);
        if (cur.nodes + add.nodes > TREE_LIMITS.maxNodes) {
            return `节点总数将达 ${cur.nodes + add.nodes}，超过编辑器上限 ${TREE_LIMITS.maxNodes}`;
        }
        const parentDepth = parentNode ? depthOf(idOf(parentNode)) : 0;
        if (parentDepth > 0 && parentDepth + add.depth > TREE_LIMITS.maxDepth) {
            return `插入后嵌套深度将达 ${parentDepth + add.depth}，超过编辑器上限 ${TREE_LIMITS.maxDepth}`;
        }
        return null;
    }

    // 某关系边当前是否已被占用。
    function hasEdge(node, relation) {
        return childEdges(node).some((edge) => edge.relation === relation);
    }
    function availableRelations(parent) {
        if (parent.type === "T") return hasEdge(parent, "next") ? [] : ["next"];
        if (parent.type === "A") return hasEdge(parent, "next") ? [] : ["next"];
        if (parent.type === "C") return hasEdge(parent, "action") ? [] : ["action"];
        if (parent.type === "D") return hasEdge(parent, "default") ? ["decision"] : ["decision", "default"];
        if (parent.type === "J") return hasEdge(parent, "action") ? [] : ["action"];
        if (["S", "P"].includes(parent.type)) return ["branch"];
        return [];
    }

    function flowNodeOperations(node) {
        const available = new Set(availableRelations(node));
        const operations = [];
        const add = (relation, label, dialogTitle) => {
            if (available.has(relation)) operations.push({ relation, label, dialogTitle });
        };
        if (node.type === "T") {
            add("next", "设置入口", "设置入口");
        } else if (node.type === "A") {
            add("next", "添加后续", "添加后续");
        } else if (["C", "J"].includes(node.type)) {
            add("action", "添加后续", "添加后续");
        } else if (node.type === "S" || node.type === "P") {
            add("branch", "添加分支", "添加分支");
        } else if (node.type === "D") {
            add("decision", "添加分支", "添加分支");
            add("default", "设置默认", "设置默认");
        }
        return operations;
    }

    function canDeleteFlowNode(node, parent, relation) {
        if (!parent) return false;
        if (["S", "P"].includes(parent.type) && relation === "branch") {
            return parent.branches.length > 1;
        }
        if (parent.type === "D") {
            const decisions = parent.branches.length;
            const hasDefault = !!parent.action;
            if (relation === "decision") {
                const remaining = decisions - 1;
                return remaining >= 1 && remaining + (hasDefault ? 1 : 0) >= 2;
            }
            if (relation === "default") {
                return decisions >= 2;
            }
        }
        return true;
    }

    // 从父节点上按 relation 移除某个子节点。
    function detachChild(parent, node, relation) {
        if (relation === "branch" || relation === "decision") {
            const idx = parent.branches.indexOf(node);
            if (idx >= 0) parent.branches.splice(idx, 1);
        } else if (relation === "next") {
            parent.next = null;
        } else if (relation === "action" || relation === "default") {
            parent.action = null;
        }
    }

    function escapeText(value) {
        return String(value ?? "").replace(/[&<>'"]/g, (char) => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
        })[char]);
    }

    function compactStructureLabel(node, type) {
        if (node.type === "L") return node.expr === "||" ? "或" : "与";
        if (node.type === "S") return "串";
        if (node.type === "P") return "并";
        if (node.type === "D") return "分";
        if (node.type === "J") return "规则";
        if (node.type !== "H") return type.short;
        const low = node.minHits == null ? "_" : String(node.minHits);
        const high = node.maxHits == null ? "_" : String(node.maxHits);
        if (high === "_" && low !== "_") return `${low}+`;
        if (low === "_") return `≤${high}`;
        if (low === high) return `=${low}`;
        return `${low}…${high}`;
    }

    // ---- 主画布渲染 ----
    function renderBranch(node, parent = null, relation = null, siblingCount = 1) {
        const type = TYPES[node.type] || TYPES.A;
        const reorderable = Boolean(parent && ["branch", "decision"].includes(relation)
            && siblingCount >= 2);
        const showExpr = node.expr && type.kind !== "structure" && type.kind !== "root";
        const expression = showExpr ? `<span class="node-expression">${escapeText(node.expr)}</span>` : "";
        const collapsible = node.type !== "J" && childNodes(node).length > 0;
        const collapsed = Boolean(collapsible && isCollapsed(node));
        const visibleEdges = node.type === "J"
            ? childEdges(node).filter((edge) => edge.relation === "action")
            : (collapsed ? [] : childEdges(node));
        const relationCounts = visibleEdges.reduce((counts, edge) => {
            counts.set(edge.relation, (counts.get(edge.relation) || 0) + 1);
            return counts;
        }, new Map());
        const children = visibleEdges.length
            ? `<div class="tree-children">${visibleEdges.map((edge) => renderBranch(
                edge.node, node, edge.relation, relationCounts.get(edge.relation))).join("")}</div>` : "";
        const hiddenCount = childNodes(node).length;
        const collapsedBadge = collapsed ? `<span class="collapsed-count" title="${hiddenCount} 个分支">${hiddenCount}</span>` : "";
        // 待配置节点（占位 PH，或未选引用规则的 动作/条件/判断）统一以红虚线“待配置”呈现。
        const pending = isUnconfigured(node);
        const title = pending
            ? `＋ ${pendingLabel(node)}`
            : (node.type === "J"
                ? ruleDisplayName(node)
                : (type.kind === "structure" || type.kind === "root"
                    ? compactStructureLabel(node, type)
                    : flowNodeDisplayName(node)));
        const cardKind = pending
            ? `placeholder${node.type === "PH" && node.slot === "decision" ? " placeholder-hex" : ""} node-type-${node.type.toLowerCase()}`
            : `${node.type === "J" ? "condition judge-summary" : type.kind} node-type-${node.type.toLowerCase()}`;
        const cardTitle = pending
            ? `${pendingLabel(node)}（点击后在右侧「引用规则」完成配置）`
            : (node.type === "J"
                ? `条件节点 · ${ruleDisplayName(node)}`
                : (["A", "C"].includes(node.type)
                    ? `${type.name} · ${flowNodeDisplayName(node)}`
                    : (flowNodeName(node) ? `${type.name} · ${flowNodeName(node)}` : type.name)));
        const jNegated = node.type === "J" && Boolean(jrule(node)?.negative);
        const negateBadge = jNegated ? `<span class="negate-badge" title="取反">非</span>` : "";
        const id = idOf(node);
        const nodeShell = `
            <div class="node-shell${collapsible ? " has-fold-toggle" : ""}"${reorderable ? ' data-reorderable="1"' : ""} data-node-id="${id}" tabindex="0" role="treeitem" aria-selected="${selectedId === id}" aria-expanded="${!collapsed}">
                    <div class="node-card ${cardKind}${jNegated ? " is-negated" : ""}" title="${escapeText(cardTitle)}">
                        <span class="node-title">${escapeText(title || type.short)}</span>
                        ${expression}
                    </div>
                    ${negateBadge}
                    ${collapsible ? `<button class="node-fold-toggle" type="button" data-action="collapse" title="${collapsed ? "展开分支" : "折叠分支"}" aria-label="${collapsed ? "展开" : "收起"}">${collapsed ? "+" : "−"}</button>` : ""}
                    ${collapsedBadge}
                </div>`;
        return `
            <div class="tree-branch" data-branch-id="${id}">
                ${nodeShell}
                ${children}
            </div>`;
    }

    function render({ preserveView = true } = {}) {
        treeRoot.innerHTML = renderBranch(tree);
        const stats = canvasStats(tree);
        $("#nodeCount").textContent = `${stats.flowNodes} 个流程节点${stats.modalRuleNodes ? ` · ${stats.modalRuleNodes} 个规则节点在弹窗` : ""}${stats.hiddenNodes ? ` · ${stats.hiddenNodes} 个流程已折叠` : ""}`;
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
        const rule = jrule(node);
        if (!rule) return "未设置";
        if (rule.type === "R") return definitionReference(RULE_DEFINITIONS, rule.expr);
        return `复合规则 · ${countNodes(rule)} 个规则节点`;
    }

    function inspectorEditableType(type) {
        return ["A", "C", "S", "P", "D", "J"].includes(type);
    }

    function editKey(nodeId, field) { return `${nodeId}:${field}`; }

    function renderInspectorHeading() {
        const container = $("#inspectorHeading");
        if (!container) return;
        container.innerHTML = `<strong id="inspectorTitle">节点属性</strong>`;
    }

    function renderInspectorFields(node) {
        const nodeName = flowNodeName(node);
        const nodeId = idOf(node);
        const parts = [];
        const pencil = (field) => `<button class="field-edit" type="button" data-field-edit="${field}" aria-label="编辑" title="编辑">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20h4L18.5 9.5a2 2 0 0 0-2.83-2.83L5 17v3z"/><path d="M13.5 6.5l4 4"/></svg>
        </button>`;
        const confirmCancel = () => `<div class="field-edit-actions">
            <button class="field-confirm" type="button" data-field-confirm aria-label="确认" title="确认"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 13l4 4L19 7"/></svg></button>
            <button class="field-cancel" type="button" data-field-cancel aria-label="取消" title="取消"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg></button>
        </div>`;
        if (inspectorEditableType(node.type)) {
            const aliasActive = inspectorEditingField === editKey(nodeId, "alias");
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
        const typeActive = typeEditable && inspectorEditingField === editKey(nodeId, "type");
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
        const ruleField = (definitions, selectedRuleId, fallbackLabel) => {
            const active = inspectorEditingField === editKey(nodeId, "expression");
            const options = [...(selectedRuleId && !definitions.some((d) => d.ruleId === selectedRuleId)
                ? [{ ruleId: selectedRuleId, desc: fallbackLabel || selectedRuleId }] : []), ...definitions];
            parts.push(`<div class="detail-field editable${active ? " field-editing" : ""}" data-field="expression">
                <span class="detail-label">引用规则</span>
                <div class="field-row">
                    <span class="detail-value">${escapeText(definitionReference(definitions, selectedRuleId, fallbackLabel))}</span>
                    ${active ? "" : pencil("expression")}
                    <select class="field-control" id="inspectorDefinitionSelect">
                        ${options.map((d) => `<option value="${escapeText(d.ruleId)}" ${d.ruleId === selectedRuleId ? "selected" : ""}>${escapeText(d.desc)} · ${escapeText(d.ruleId)}</option>`).join("")}
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
            ruleField(ACTION_DEFINITIONS, node.expr, flowNodeName(node));
            if (node.next) readonlyField("流程", flowNodeDisplayName(node.next));
        } else if (node.type === "C") {
            ruleField(RULE_DEFINITIONS, node.expr, flowNodeName(node));
        } else if (node.type === "J") {
            readonlyField("引用规则", judgeRuleReference(node));
        } else if (["S", "P"].includes(node.type)) {
            readonlyField("分支数", `${node.branches.length} 个`);
        } else if (node.type === "D") {
            readonlyField("分支数", `${node.branches.length} 个`);
            readonlyField("默认分支", node.action ? "已设置" : "未设置");
        } else {
            readonlyField("流程入口", node.next ? flowNodeDisplayName(node.next) : "未设置");
        }
        $("#nodeDetailFields").innerHTML = parts.join("");
        if (inspectorEditingField && inspectorEditingField.startsWith(`${nodeId}:`)) {
            requestAnimationFrame(() => {
                const control = $("#nodeDetailFields .field-editing .field-control");
                control?.focus();
                if (control?.tagName === "INPUT") control.select();
            });
        }
    }

    function updateInspector() {
        const found = selectedId ? findNode(selectedId) : null;
        const empty = $("#inspectorEmpty");
        const form = $("#inspectorForm");
        if (!found) { empty.hidden = false; form.hidden = true; return; }
        const { node } = found;
        empty.hidden = true;
        form.hidden = false;
        renderInspectorHeading();
        // 占位节点：提示 + “配置此节点”按钮（点节点本身也会弹「配置节点」弹窗）。
        if (node.type === "PH") {
            const isDecision = node.slot === "decision";
            $("#nodeDetailFields").innerHTML = `<div class="detail-field"><span class="detail-value">${isDecision ? "决策分支（固定为条件），点击配置条件。" : "尚未配置，点击选择节点类型并完成配置。"}</span></div>`;
            $("#flowNodeActions").innerHTML = `<button class="flow-operation wide add-op" type="button" data-config-ph>配置此节点</button>`;
            $("#editNodeButton").hidden = true;
            $("#inspectorViewActions").hidden = false;
            $("#inspectorAddPanel").hidden = true;
            $("#inspectorFooter").hidden = false;
            const canDel = canDeleteFlowNode(node, found.parent, found.relation);
            $("#deleteButton").disabled = !canDel;
            $("#deleteButton").title = canDel ? "" : "至少保留一个分支";
            return;
        }
        renderInspectorFields(node);
        $("#inspectorAddPanel").hidden = true;
        const operations = flowNodeOperations(node);
        // 主操作（添加后续/分支）在前、强调；次操作「前插节点」在后、弱化并分隔，避免误点。
        const addButtons = operations.map((operation) => `
            <button class="flow-operation wide add-op" type="button" data-flow-relation="${operation.relation}">
                ${escapeText(operation.label)}
            </button>`).join("");
        const insertButton = node.type !== "T"
            ? `<button class="flow-operation wide insert-op" type="button" data-insert-before title="在当前节点之前插入一个新节点（会包裹当前节点）">向前插入</button>`
            : "";
        $("#flowNodeActions").innerHTML = addButtons + insertButton;
        const editButton = $("#editNodeButton");
        editButton.hidden = node.type !== "J";
        // 插入节点按钮对所有非根节点都存在，故只要不是根节点该操作区就应可见，
        // 否则「有后继、非判断」的节点（如根下第一个动作节点）会误藏插入入口。
        const hasInsert = node.type !== "T";
        $("#inspectorViewActions").hidden = !hasInsert && operations.length === 0 && editButton.hidden;
        $("#inspectorFooter").hidden = node.type === "T";
        const deletable = canDeleteFlowNode(node, found.parent, found.relation);
        $("#deleteButton").disabled = !deletable;
        $("#deleteButton").title = deletable ? "" : "当前节点是所属结构的唯一必需分支，不能直接删除";
    }

    function beginFieldEdit(field) {
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !inspectorEditableType(found.node.type)) return;
        inspectorEditingField = editKey(idOf(found.node), field);
        renderInspectorHeading();
        renderInspectorFields(found.node);
    }

    function confirmFieldEdit() {
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !inspectorEditingField) return;
        const node = found.node;
        const before = JSON.stringify(node);
        const field = inspectorEditingField.split(":")[1];
        if (field === "alias") {
            setNodeAlias(node, $("#inspectorAliasInput").value.trim());
        } else if (field === "expression" && ["A", "C"].includes(node.type)) {
            const definitions = node.type === "A" ? ACTION_DEFINITIONS : RULE_DEFINITIONS;
            const definition = definitions.find((candidate) => candidate.ruleId === $("#inspectorDefinitionSelect").value);
            if (definition) node.expr = definition.ruleId;
        } else if (field === "expression" && node.type === "J") {
            // 就地设置判断节点的根规则（单个规则）；保留原规则名与取反。
            const definition = RULE_DEFINITIONS.find((candidate) => candidate.ruleId === $("#inspectorDefinitionSelect").value);
            if (definition) {
                const r = jrule(node);
                if (r && r.type === "R") { r.expr = definition.ruleId; }
                else { node.rule = make.atom(definition.ruleId); }
            }
        } else if (field === "type" && ["S", "P"].includes(node.type)) {
            const newType = $("#inspectorTypeSelect").value;
            if (["S", "P"].includes(newType)) { node.type = newType; node.expr = newType; }
        }
        inspectorEditingField = null;
        render({ preserveView: true });
        if (before !== JSON.stringify(node)) markDraft();
    }

    // 流程节点名称写入 label；J 写入其规则根 name；规则节点写入 name。
    function setNodeAlias(node, value) {
        if (node.type === "J") { const rule = jrule(node); if (rule) rule.name = value; }
        else if (["R", "L", "H"].includes(node.type)) node.name = value;
        else node.label = value;
    }

    function cancelFieldEdit() {
        inspectorEditingField = null;
        const found = selectedId ? findNode(selectedId) : null;
        if (found) { renderInspectorHeading(); renderInspectorFields(found.node); }
    }

    function selectNode(id) {
        if (id !== selectedId) inspectorEditingField = null;
        selectedId = id;
        treeRoot.querySelectorAll(".node-shell").forEach((shell) => {
            shell.setAttribute("aria-selected", String(shell.dataset.nodeId === id));
        });
        updateInspector();
    }

    function confirmDiscardInspectorEdit() { inspectorEditingField = null; return true; }

    // ---- 规则弹窗（在 J 的规则子树上操作）----
    function currentRuleJudge() {
        const found = ruleJudgeId ? findNode(ruleJudgeId) : null;
        return found?.node.type === "J" ? found.node : null;
    }

    // 在当前 J 的规则子树中定位规则节点，返回 {node, parent}。parent 为 L/H 或 J（根规则）。
    function findRuleNode(id) {
        const judge = currentRuleJudge();
        const root = judge ? jrule(judge) : null;
        if (!root || !id) return null;
        let found = null;
        const visitRule = (node, parent) => {
            if (idOf(node) === id) { found = { node, parent }; return false; }
            for (const child of childNodes(node)) {
                if (visitRule(child, node) === false) return false;
            }
            return true;
        };
        visitRule(root, judge);
        return found;
    }

    function renderRuleBranch(node, parent = null) {
        const type = TYPES[node.type] || TYPES.R;
        const reorderable = Boolean(parent && ["L", "H"].includes(parent.type) && (parent.rules?.length || 0) >= 2);
        const showExpr = node.type !== "H" && node.expr;
        const expression = showExpr ? `<span class="node-expression">${escapeText(node.expr)}</span>` : "";
        const kids = childNodes(node);
        const children = kids.length
            ? `<div class="rule-children">${kids.map((child) => renderRuleBranch(child, node)).join("")}</div>` : "";
        const title = type.kind === "structure" ? compactStructureLabel(node, type) : ruleNodeDisplayName(node);
        const negateBadge = node.negative ? `<span class="negate-badge" title="取反">非</span>` : "";
        const id = idOf(node);
        return `
            <div class="rule-branch" data-rule-branch-id="${id}">
                <div class="node-shell"${reorderable ? ' data-reorderable="1"' : ""} data-rule-node-id="${id}" tabindex="0" role="treeitem" aria-selected="${ruleSelectedId === id}">
                    <div class="node-card ${type.kind} node-type-${node.type.toLowerCase()}${node.negative ? " is-negated" : ""}" aria-label="${escapeText(`${node.negative ? "取反 · " : ""}${type.name} · ${ruleNodeDisplayName(node)}`)}">
                        <span class="node-title">${escapeText(title || type.short)}</span>
                        ${expression}
                    </div>
                    ${negateBadge}
                </div>
                ${children}
            </div>`;
    }

    function canDeleteRule(found) {
        if (!found) return false;
        if (found.parent?.type === "J") return false;
        if (found.parent?.type === "H" && (found.parent.rules?.length || 0) <= 1) return false;
        return true;
    }

    function renderRuleTree() {
        const judge = currentRuleJudge();
        const rule = judge ? jrule(judge) : null;
        if (!judge) return;
        if (ruleSelectedId && !findRuleNode(ruleSelectedId)) ruleSelectedId = rule ? idOf(rule) : null;
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
        if (node.type === "R") return [node];
        if (["L", "H"].includes(node.type)) return node.rules.slice();
        return [];
    }

    function subruleRowLabel(child) {
        if (child.type === "R") {
            const definition = ruleDefinitionById(child.expr);
            return definition ? `${definition.desc} · ${definition.ruleId}` : (child.expr || "未设置");
        }
        return `${ruleNodeDisplayName(child)} · ${countNodes(child)} 个规则节点`;
    }

    function renderRuleEditorHeading() {
        const container = $("#ruleEditorHeading");
        if (container) container.innerHTML = `<strong>节点属性</strong>`;
    }

    function renderRuleEditorSubrules() {
        const container = $("#ruleEditorSubrules");
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node) { container.innerHTML = ""; $("#ruleEditorAddRuleButton").hidden = true; renderRuleLogicField(); return; }
        const refs = ruleNodeSubrules(node);
        const canReorder = node.type === "L";
        container.innerHTML = refs.map((child, index) => {
            const lastIndex = refs.length - 1;
            const activeClass = idOf(child) === highlightedRuleRowId ? " rule-row-active" : "";
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
                    ${RULE_DEFINITIONS.map((definition) => `<option value="${escapeText(definition.ruleId)}" ${definition.ruleId === child.expr ? "selected" : ""}>${escapeText(definition.desc)} · ${escapeText(definition.ruleId)}</option>`).join("")}
                </select>
                ${moveButtons}
            </div>`;
        }).join("");
        $("#ruleEditorAddRuleButton").hidden = !["R", "L", "H"].includes(node.type);
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
        if (node.type === "R") {
            ruleLogicEditing = false;
            container.className = "detail-field";
            container.innerHTML = `<span class="detail-label">节点类型</span>
                <div class="field-row"><span class="detail-value">原子节点</span></div>`;
            return;
        }
        const label = node.expr === "||" ? "逻辑或" : "逻辑与";
        container.className = "detail-field editable" + (ruleLogicEditing ? " field-editing" : "");
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
                ${ruleLogicEditing ? "" : pencil}
                <select class="field-control" id="ruleLogicSelect">
                    <option value="&&" ${node.expr !== "||" ? "selected" : ""}>逻辑与</option>
                    <option value="||" ${node.expr === "||" ? "selected" : ""}>逻辑或</option>
                </select>
                ${ruleLogicEditing ? confirmCancel : ""}
            </div>`;
    }

    function beginRuleLogicEdit() {
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node || node.type !== "L") return;
        ruleLogicEditing = true;
        renderRuleLogicField();
    }

    function confirmRuleLogicEdit() {
        const found = findRuleNode(ruleSelectedId);
        if (!found || found.node.type !== "L") { ruleLogicEditing = false; renderRuleLogicField(); return; }
        found.node.expr = $("#ruleLogicSelect")?.value === "||" ? "||" : "&&";
        ruleLogicEditing = false;
        renderRuleLogicField();
        renderRuleTree();
        render({ preserveView: true });
    }

    function cancelRuleLogicEdit() { ruleLogicEditing = false; renderRuleLogicField(); }

    function updateRuleEditor() {
        ruleLogicEditing = false;
        highlightedRuleRowId = null;
        const found = findRuleNode(ruleSelectedId);
        const empty = $("#ruleEditorEmpty");
        const panel = $("#ruleEditorPanel");
        if (!found) {
            const hasRule = Boolean(currentRuleJudge() && jrule(currentRuleJudge()));
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
        renderRuleEditorHeading();
        const isHits = node.type === "H";
        $("#ruleReferenceField").hidden = !isHits;
        $("#ruleNodeExpression").value = isHits ? compactStructureLabel(node, TYPES.H) : "";
        $("#ruleNodeExpression").readOnly = true;
        $("#ruleEditorSubrulesField").hidden = false;
        renderRuleEditorSubrules();
        $("#ruleNegateField").hidden = false;
        $("#ruleNegateInput").checked = !!node.negative;
        $("#ruleAddPanel").hidden = true;
        $("#ruleEditorFooter").hidden = false;
        const deletable = canDeleteRule(found);
        $("#ruleEditorDeleteButton").disabled = !deletable;
        $("#ruleEditorDeleteButton").title = deletable
            ? ""
            : (found.parent?.type === "J"
                ? "判断节点必须保留根规则，不能删除"
                : "组合规则至少需要保留两个子规则，不能直接删除");
    }

    function currentEditingNode() { return findRuleNode(ruleSelectedId)?.node || null; }

    // 用幸存子规则替换 L 节点：L 是 J 的根规则则替换 J.rule，否则替换父 L/H.rules 中的该项。
    function collapseLogicToSurvivor(logicNode, survivor) {
        const found = findRuleNode(idOf(logicNode));
        const parent = found ? found.parent : currentRuleJudge();
        if (!parent) return;
        if (parent.type === "J") {
            parent.rule = survivor;
        } else if (["L", "H"].includes(parent.type)) {
            const idx = parent.rules.indexOf(logicNode);
            if (idx >= 0) parent.rules.splice(idx, 1, survivor);
        }
        ruleSelectedId = idOf(survivor);
    }

    function removeRuleEditorSubrule(index) {
        const node = currentEditingNode();
        if (!node || !["L", "H"].includes(node.type)) return;
        if (index < 0 || index >= node.rules.length) return;
        if (node.type === "L" && node.rules.length <= 2) {
            const survivor = node.rules[index === 0 ? 1 : 0];
            collapseLogicToSurvivor(node, survivor);
            renderRuleTree();
            updateRuleEditor();
            render({ preserveView: true });
            return;
        }
        node.rules.splice(index, 1);
        renderRuleTree();
        renderRuleEditorSubrules();
        render({ preserveView: true });
    }

    function moveRuleEditorSubrule(index, direction) {
        const node = currentEditingNode();
        if (!node || !["L", "H"].includes(node.type)) return;
        const list = node.rules;
        const target = index + direction;
        if (index < 0 || index >= list.length || target < 0 || target >= list.length) return;
        [list[index], list[target]] = [list[target], list[index]];
        highlightedRuleRowId = idOf(list[target]);
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

    // ---- 组合规则构建（L 编辑器内联）----
    let ruleCompositeRuleIds = [];

    let compositePromoteFrom = null; // R node being promoted to composite (null = adding to existing L/H)

    function openCompositeBuilder() {
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node || !["R", "L", "H"].includes(node.type)) return;
        compositePromoteFrom = node.type === "R" ? node : null;
        const firstId = compositePromoteFrom
            ? (RULE_DEFINITIONS.find((d) => d.ruleId !== compositePromoteFrom.expr)?.ruleId || RULE_DEFINITIONS[0]?.ruleId || "")
            : (RULE_DEFINITIONS[0]?.ruleId || "");
        ruleCompositeRuleIds = [firstId];
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
            </div>`).join("");
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
        compositePromoteFrom = null;
        document.getElementById("ruleAddDialog").close();
    }

    function confirmCompositeBuilder() {
        const found = findRuleNode(ruleSelectedId);
        const node = found?.node;
        if (!node || ruleCompositeRuleIds.length < 1) return;
        // Read latest select values
        $("#ruleCompositeRows").querySelectorAll("[data-composite-select]").forEach((select) => {
            ruleCompositeRuleIds[Number(select.dataset.compositeSelect)] = select.value;
        });
        const logic = document.querySelector('input[name="ruleCompositeLogic"]:checked')?.value || "&&";

        if (compositePromoteFrom && node.type === "R") {
            // 单规则转复合：创建 LNode，把原 R 保留为第一个子规则，新选的追加其后
            const lNode = make.logic(logic);
            lNode.name = "";
            // 保留原 R 的名称、取反状态和引用
            const originalR = { type: "R", expr: node.expr, negative: node.negative };
            if (node.name) originalR.name = node.name;
            lNode.rules.push(originalR);
            ruleCompositeRuleIds.forEach((ruleId) => {
                const r = createAtomicRule(ruleId);
                if (r) lNode.rules.push(r);
            });
            if (lNode.rules.length < 2) return;
            const blocked = limitBlockReason(node, lNode);
            if (blocked) {
                document.getElementById("ruleAddDialog").close();
                ruleCompositeRuleIds = []; compositePromoteFrom = null;
                openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" });
                return;
            }
            // 替换：parent 可能是 J（rule 边）或 L/H（rules 数组）
            const parent = found.parent;
            if (parent.type === "J") {
                parent.rule = lNode;
            } else if (["L", "H"].includes(parent.type) && Array.isArray(parent.rules)) {
                const idx = parent.rules.indexOf(node);
                if (idx >= 0) parent.rules.splice(idx, 1, lNode);
            }
            document.getElementById("ruleAddDialog").close();
            ruleCompositeRuleIds = []; compositePromoteFrom = null;
            ruleSelectedId = idOf(lNode);
            renderRuleTree();
            updateRuleEditor();
        } else if (["L", "H"].includes(node.type)) {
            // 已有组合：直接追加子规则
            let newNode;
            if (ruleCompositeRuleIds.length === 1) {
                newNode = createAtomicRule(ruleCompositeRuleIds[0]);
            } else {
                newNode = createLogicRule(logic, "", ruleCompositeRuleIds);
            }
            if (!newNode) return;
            const blocked = limitBlockReason(node, newNode);
            if (blocked) {
                document.getElementById("ruleAddDialog").close();
                ruleCompositeRuleIds = []; compositePromoteFrom = null;
                openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" });
                return;
            }
            node.rules.push(newNode);
            document.getElementById("ruleAddDialog").close();
            ruleCompositeRuleIds = []; compositePromoteFrom = null;
            renderRuleTree();
            updateRuleEditor();
        }
    }

    function renderRuleDialog() {
        const judge = currentRuleJudge();
        if (!judge) return;
        $("#ruleDialogTitle").textContent = ruleDisplayName(judge);
        renderRuleTree();
        updateRuleEditor();
    }

    let ruleDialogSnapshot = null;
    let ruleDialogCommitted = false;

    function cloneNode(node) { return node ? T.deserialize(T.serialize(node)) : null; }

    function openRuleDialog(judgeId, rollbackOnCancel = null) {
        const found = findNode(judgeId);
        if (!found || found.node.type !== "J") return;
        ruleJudgeId = judgeId;
        ruleDialogSnapshot = {
            rule: cloneNode(found.node.rule),
            action: cloneNode(found.node.action),
            rollbackOnCancel
        };
        ruleDialogCommitted = false;
        const rule = jrule(found.node);
        ruleSelectedId = rule ? idOf(rule) : null;
        hideRulePopover();
        renderRuleDialog();
        if (!ruleDialog.open) ruleDialog.showModal();
    }

    function hideRulePopover() { $("#ruleAddPanel").hidden = true; }

    function ruleDefinitionById(ruleId) { return RULE_DEFINITION_BY_ID.get(ruleId) || null; }
    function actionDefinitionById(ruleId) { return ACTION_DEFINITION_BY_ID.get(ruleId) || null; }

    function createAtomicRule(ruleId, name = "") {
        const definition = ruleDefinitionById(ruleId);
        if (!definition) return null;
        return make.atom(definition.ruleId, name.trim());
    }

    function createLogicRule(operator, name, ruleIds) {
        const rules = ruleIds.map((ruleId) => createAtomicRule(ruleId));
        if (rules.some((rule) => !rule)) return null;
        const node = make.logic(operator);
        node.name = name.trim();
        rules.forEach((rule) => node.rules.push(rule));
        return node;
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
            if (focusIndex >= 0) $(`[data-rule-add-select="${focusIndex}"]`)?.focus();
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

    function startRuleAdd() {
        const judge = currentRuleJudge();
        if (!judge || jrule(judge)) return;
        $("#ruleEditorEmpty").hidden = true;
        $("#ruleEditorPanel").hidden = false;
        $("#ruleEditorHeading").innerHTML = "";
        $("#ruleEditorSubrulesField").hidden = true;
        $("#ruleEditorLogicField").hidden = true;
        $("#ruleNegateField").hidden = true;
        $("#ruleReferenceField").hidden = true;
        $("#ruleAddTitle").textContent = "配置根规则";
        ruleAddRuleIds = [RULE_DEFINITIONS[0]?.ruleId || ""];
        document.querySelector('input[name="ruleAddLogic"][value="&&"]').checked = true;
        $("#ruleEditorFooter").hidden = true;
        $("#ruleAddPanel").hidden = false;
        renderRuleAddRows(0);
    }

    function cancelRuleAdd() {
        ruleAddRuleIds = [];
        $("#ruleAddPanel").hidden = true;
        updateRuleEditor();
    }

    function confirmRuleAdd() {
        const judge = currentRuleJudge();
        if (!judge || ruleAddRuleIds.length === 0 || jrule(judge)) return;
        const node = ruleAddRuleIds.length === 1
            ? createAtomicRule(ruleAddRuleIds[0])
            : createLogicRule(document.querySelector('input[name="ruleAddLogic"]:checked').value, "", ruleAddRuleIds);
        if (!node) return;
        const blocked = limitBlockReason(judge, node);
        if (blocked) { openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" }); return; }
        judge.rule = node;
        ruleSelectedId = idOf(node);
        ruleAddRuleIds = [];
        hideRulePopover();
        renderRuleDialog();
        render({ preserveView: true });
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
        if (!parent || !["L", "H"].includes(parent.type)) return;
        const idx = parent.rules.indexOf(found.node);
        if (idx >= 0) parent.rules.splice(idx, 1);
        if (parent.type === "L" && parent.rules.length === 1) {
            collapseLogicToSurvivor(parent, parent.rules[0]);
        } else {
            ruleSelectedId = ["L", "H"].includes(parent.type) ? idOf(parent) : null;
        }
        renderRuleDialog();
        render({ preserveView: true });
    }

    // 判断节点是否处于“待配置”：占位 PH，或动作/条件/判断尚未选择引用规则（J 规则为空或空原子）。
    function isUnconfigured(node) {
        if (node.type === "PH") return true;
        if (node.type === "A" || node.type === "C") return !((node.expr || "").trim());
        if (node.type === "J") { const r = jrule(node); return !r || (r.type === "R" && !((r.expr || "").trim())); }
        return false;
    }
    function pendingLabel(node) {
        if (node.type === "A") return "待配置动作";
        if (node.type === "C" || node.type === "J") return "待配置条件";
        if (node.type === "PH" && node.slot === "decision") return "待配置条件";
        return "待配置";
    }

    // 新建结构节点（串/并/分）时带占位脚手架，保证“添加即完整、无空壳”：
    // 串/并 → 两个分支占位；分 → 一个条件槽 + 一个默认槽。占位由用户就地配置替换。
    function buildStructuralScaffold(type) {
        if (type === "S" || type === "P") {
            const node = type === "S" ? make.serial() : make.parallel();
            node.branches.push(make.placeholder("branch"), make.placeholder("branch"));
            return node;
        }
        if (type === "D") {
            const node = make.decision();
            node.branches.push(make.placeholder("decision"));
            node.action = make.placeholder("default");
            return node;
        }
        return null;
    }

    // 统计树中“待配置”节点数量（占位 PH + 未选引用规则的 动作/条件/判断）。
    function countPlaceholders() {
        let n = 0;
        walk(tree, (node) => { if (isUnconfigured(node)) n++; });
        return n;
    }

    // 把新建节点挂到父节点的目标关系边上（列表边支持定位）。
    function attachChild(parent, relation, node, placement, anchorId) {
        if (relation === "next") { parent.next = node; return; }
        if (relation === "action") { parent.action = node; return; }
        if (relation === "default") { parent.action = node; return; }
        // list edge: branch / decision -> parent.branches
        const list = parent.branches;
        if (placement === "first") { list.unshift(node); return; }
        if (placement === "before" || placement === "after") {
            const anchorIndex = list.findIndex((child) => idOf(child) === anchorId);
            if (anchorIndex >= 0) { list.splice(placement === "before" ? anchorIndex : anchorIndex + 1, 0, node); return; }
        }
        list.push(node);
    }

    function openInsertPanel() {
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || found.node.type === "T") return;
        if (found.relation === "decision") {
            openConfirm("决策分支只能是条件节点，不能在其前插入其它结构。", { title: "无法插入", confirmLabel: "知道了" });
            return;
        }
        insertBeforeMode = true;
        $("#inspectorAddTitle").textContent = "向前插入（包裹为结构）";
        populateTypeSelect($("#newNodeType"), ["S", "P"]);
        $("#newNodeType").value = "S";
        $("#inspectorViewActions").hidden = true;
        $("#inspectorFooter").hidden = true;
        $("#inspectorAddPanel").hidden = false;
        requestAnimationFrame(() => { $("#newNodeType").focus(); });
    }

    function closeAddPanel() {
        insertBeforeMode = false;
        $("#inspectorAddPanel").hidden = true;
        updateInspector();
    }

    // 用真实节点替换占位/目标（按其所在的关系边）。
    function replaceChild(parent, oldNode, relation, real) {
        if (relation === "branch" || relation === "decision") {
            const idx = parent.branches.indexOf(oldNode);
            if (idx >= 0) parent.branches.splice(idx, 1, real);
        } else if (relation === "default" || relation === "action") {
            parent.action = real;
        } else if (relation === "next") {
            parent.next = real;
        }
    }

    function relationToSlot(relation) {
        if (relation === "decision") return "decision";
        if (relation === "branch") return "branch";
        if (relation === "default") return "default";
        return "next";
    }

    // “添加后续/分支/默认/入口”：不提前落占位，仅记录目标（父节点+关系），随即打开配置弹窗；
    // 确认后才把真实节点挂到目标位置。决策分支固定为条件，直接插入判断并打开条件编辑，不经类型弹窗。
    let configTarget = null;
    function startAddConfig(relation) {
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !availableRelations(found.node).includes(relation)) return;
        const parent = found.node;
        const probe = make.placeholder(relationToSlot(relation));
        const blocked = limitBlockReason(parent, probe);
        if (blocked) { openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" }); return; }
        if (relation === "decision") {
            setCollapsed(parent, false);
            const j = make.judge(null);
            j.rule = null;
            attachChild(parent, relation, j, "last", null);
            selectedId = idOf(j);
            fitMode = false;
            render({ preserveView: true });
            const rollback = () => {
                const location = findNode(idOf(j));
                if (location) detachChild(location.parent, j, location.relation);
                selectedId = idOf(parent);
            };
            requestAnimationFrame(() => openRuleDialog(idOf(j), rollback));
            return;
        }
        configTarget = { mode: "add", parentId: idOf(parent), relation };
        openConfigDialog();
    }

    function openConfigDialog() {
        const dlg = document.getElementById("nodeConfigDialog");
        $("#nodeConfigType").value = "A";
        syncNodeConfigRule();
        if (!dlg.open) dlg.showModal();
    }

    // 点击画布上已有的“待配置”占位（脚手架槽位）进入配置：replace 模式。
    // 决策槽固定为条件→直接开条件编辑弹窗；其余槽弹「配置节点」三选一。
    function openNodeConfig(ph) {
        if (!ph || ph.type !== "PH") return;
        if (ph.slot === "decision") { configureAsCondition(ph); return; }
        configTarget = { mode: "replace", phId: idOf(ph) };
        openConfigDialog();
    }
    function syncNodeConfigRule() {
        const type = $("#nodeConfigType").value;
        const ruleField = $("#nodeConfigRuleField");
        const note = $("#nodeConfigNote");
        if (type === "A") {
            ruleField.hidden = false;
            $("#nodeConfigRule").innerHTML = ACTION_DEFINITIONS
                .map((d) => `<option value="${escapeText(d.ruleId)}">${escapeText(d.desc)} · ${escapeText(d.ruleId)}</option>`).join("");
            note.textContent = ACTION_DEFINITIONS.length ? "" : "暂无执行动作，请先在「业务规则」中新建执行动作。";
        } else if (type === "J") {
            ruleField.hidden = true;
            note.textContent = "确认后打开条件编辑，配置该条件（可原子 / 与或 / 命中）。";
        } else {
            ruleField.hidden = true;
            note.textContent = "确认后生成对应结构，其分支再在画布上逐个配置。";
        }
        note.hidden = !note.textContent.trim();
    }
    function closeNodeConfig() {
        const dlg = document.getElementById("nodeConfigDialog");
        if (dlg.open) dlg.close();
        configTarget = null;
    }
    // 按 configTarget 把真实节点落到目标位置（add=挂到父节点关系边；replace=顶替占位）。返回落定后的节点或 null。
    function placeConfigured(real) {
        if (!configTarget) return null;
        if (configTarget.mode === "add") {
            const parent = findNode(configTarget.parentId)?.node;
            if (!parent) return null;
            setCollapsed(parent, false);
            attachChild(parent, configTarget.relation, real, "last", null);
            return real;
        }
        const found = findNode(configTarget.phId);
        if (!found || found.node.type !== "PH") return null;
        replaceChild(found.parent, found.node, found.relation, real);
        return real;
    }
    function confirmNodeConfig() {
        if (!configTarget) { closeNodeConfig(); return; }
        const type = $("#nodeConfigType").value;
        if (type === "A") {
            const rid = $("#nodeConfigRule").value;
            if (!rid) return;
            const real = placeConfigured(make.action(rid));
            closeNodeConfig();
            if (real) { selectedId = idOf(real); render({ preserveView: true }); markDraft(); }
        } else if (["S", "P", "D"].includes(type)) {
            const real = placeConfigured(buildStructuralScaffold(type));
            closeNodeConfig();
            if (real) { selectedId = idOf(real); render({ preserveView: true }); markDraft(); }
        } else if (type === "J") {
            const target = configTarget;
            closeNodeConfig();
            const j = make.judge(null);
            j.rule = null;
            let rollback;
            if (target.mode === "add") {
                const parent = findNode(target.parentId)?.node;
                if (!parent) return;
                setCollapsed(parent, false);
                attachChild(parent, target.relation, j, "last", null);
                rollback = () => {
                    const location = findNode(idOf(j));
                    if (location) detachChild(location.parent, j, location.relation);
                    selectedId = idOf(parent);
                };
            } else {
                const found = findNode(target.phId);
                if (!found || found.node.type !== "PH") return;
                const { node: placeholder } = found;
                replaceChild(found.parent, found.node, found.relation, j);
                rollback = () => {
                    const location = findNode(idOf(j));
                    if (location) replaceChild(location.parent, j, location.relation, placeholder);
                    selectedId = idOf(placeholder);
                };
            }
            selectedId = idOf(j);
            fitMode = false;
            render({ preserveView: true });
            requestAnimationFrame(() => openRuleDialog(idOf(j), rollback));
        }
    }

    // 条件配置：把已有占位替换成判断节点（规则暂空=待配置条件），随即打开条件编辑弹窗。
    function configureAsCondition(ph) {
        const found = findNode(idOf(ph));
        if (!found || found.node.type !== "PH") return;
        const j = make.judge(null);
        j.rule = null;
        const { node: placeholder, parent, relation } = found;
        replaceChild(parent, placeholder, relation, j);
        configTarget = null;
        selectedId = idOf(j);
        fitMode = false;
        render({ preserveView: true });
        const rollback = () => {
            const location = findNode(idOf(j));
            if (location) replaceChild(location.parent, j, location.relation, placeholder);
            selectedId = idOf(placeholder);
        };
        requestAnimationFrame(() => openRuleDialog(idOf(j), rollback));
    }

    function insertNodeBefore() {
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !found.parent) return;
        const { node: target, parent, relation } = found;
        const type = $("#newNodeType").value;
        // 向前插入只支持包裹为结构（串/并）：新结构含 target + 一个待配置占位分支。
        if (!["S", "P"].includes(type)) return;
        const node = buildStructuralScaffold(type);   // 含 2 个占位分支
        if (!node) return;
        // 用 target 顶替新结构的第一个占位分支，另一个占位保留待配置。
        node.branches[0] = target;
        const added = inspectTree(node).nodes - inspectTree(target).nodes;
        const cur = inspectTree(tree).nodes;
        if (cur + added > TREE_LIMITS.maxNodes) {
            openConfirm(`节点总数将达 ${cur + added}，超过编辑器上限 ${TREE_LIMITS.maxNodes}`, { title: "超出编辑器上限", confirmLabel: "知道了" });
            return;
        }
        const projectedDepth = depthOf(idOf(target)) + 2;
        if (projectedDepth > TREE_LIMITS.maxDepth) {
            openConfirm(`插入后嵌套深度将达 ${projectedDepth}，超过编辑器上限 ${TREE_LIMITS.maxDepth}`, { title: "超出编辑器上限", confirmLabel: "知道了" });
            return;
        }
        // 把新结构放回 target 原来的位置。
        if (relation === "branch" || relation === "decision") {
            const idx = parent.branches.indexOf(target);
            if (idx < 0) return;
            parent.branches.splice(idx, 1, node);
        } else if (relation === "next") {
            parent.next = node;
        } else if (relation === "action" || relation === "default") {
            parent.action = node;
        } else {
            return;
        }
        selectedId = idOf(node);
        insertBeforeMode = false;
        $("#inspectorAddPanel").hidden = true;
        fitMode = false;
        render({ preserveView: true });
        markDraft();
        return node;
    }

    async function deleteSelected() {
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !canDeleteFlowNode(found.node, found.parent, found.relation)) return;
        const descendants = countFlowNodes(found.node) - 1;
        const nodeName = flowNodeDisplayName(found.node);
        const nodeId = idOf(found.node);
        const message = descendants
            ? `确定删除「${nodeName}」及其 ${descendants} 个流程子节点吗？`
            : `确定删除「${nodeName}」吗？`;
        if (!(await openConfirm(message, { title: "确认删除", confirmLabel: "删除" }))) return;
        const current = findNode(nodeId);
        if (!current || !canDeleteFlowNode(current.node, current.parent, current.relation)) return;
        detachChild(current.parent, current.node, current.relation);
        selectedId = idOf(current.parent);
        render({ preserveView: true });
        markDraft();
    }

    function toggleCollapse() {
        const found = selectedId ? findNode(selectedId) : null;
        if (!found) return;
        if (!childNodes(found.node).length || found.node.type === "J") return;
        setCollapsed(found.node, !isCollapsed(found.node));
        render({ preserveView: true });
    }

    function updateDocStatus() {
        const textEl = $("#docStatusText");
        const pill = $("#docStatus");
        if (!textEl || !pill) return;
        textEl.textContent = dirty ? "有未保存修改" : docStatus === "formal" ? "已生效" : docStatus === "disabled" ? "已停用" : "草稿";
        pill.classList.toggle("status-formal", !dirty && docStatus === "formal");
    }

    function markDraft() {
        dirty = true;
        updateDocStatus();
    }

    function pulseStatus() {
        const pill = $("#docStatus");
        if (pill) { pill.classList.remove("status-pulse"); void pill.offsetWidth; pill.classList.add("status-pulse"); }
    }

    function setSaveBusy(busy) {
        const a = $("#saveDraftButton"), b = $("#promoteButton");
        const off = busy || flowLoadFailed;
        if (a) a.disabled = off;
        if (b) b.disabled = off;
    }

    // 存在待配置占位时拦截保存/生效（占位无后端表示，无法序列化）。返回 true 表示已拦截。
    function placeholderGate() {
        const n = countPlaceholders();
        if (n > 0) {
            openConfirm(`还有 ${n} 个待配置节点，请先完成配置再保存/生效。`, { title: "存在待配置节点", confirmLabel: "知道了" });
            return true;
        }
        return false;
    }

    // 保存到后端：过滤瞬时态后 serialize(tree) → 草稿走 PUT /api/flows/{id}，生效走 POST /publish；
    // 不再由前端传 status（后端按端点决定草稿/生效，PUT 不能发布）。
    async function saveToBackend(makeFormal) {
        if (savingFlow) return true;
        if (placeholderGate()) return false;
        if (!flowMeta || !window.MousikaApi) {
            docStatus = makeFormal ? "formal" : "draft";
            dirty = false;
            updateDocStatus(); pulseStatus();
            return true;
        }
        savingFlow = true; setSaveBusy(true);
        try {
            const payload = {
                id: flowMeta.id, name: flowMeta.name, description: flowMeta.description,
                ruleTree: JSON.stringify(T.serialize(tree)), version: flowMeta.version
            };
            const updated = makeFormal
                ? await window.MousikaApi.publishFlow(flowMeta.id, payload)
                : await window.MousikaApi.updateFlow(flowMeta.id, payload);
            if (updated) { flowMeta.version = updated.version; flowMeta.name = updated.name; flowMeta.description = updated.description; }
            docStatus = makeFormal ? "formal" : "draft";
            dirty = false;
            updateDocStatus(); pulseStatus();
            // 保存/生效成功后自动返回场景列表（dirty 已清空，不再触发离开提醒）。
            setTimeout(() => { location.href = "/"; }, 300);
            return true;
        } catch (e) {
            openConfirm(`${makeFormal ? "生效" : "保存"}失败：${e.message}`,
                { title: makeFormal ? "生效失败" : "保存失败", confirmLabel: "知道了" });
            return false;
        } finally {
            savingFlow = false; setSaveBusy(false);
        }
    }

    function saveDraft() { saveToBackend(false); }

    function collectViolations() {
        const problems = [];
        walk(tree, (node) => {
            if (node.type !== "D") return true;
            // 与后端 TreeVisitor 契约一致：分支节点至少两个结果
            // （决策分支+默认分支合计≥2）；决策分支的命中后续动作可以为空。
            const decisions = node.branches.length;
            const hasDefault = !!node.action;
            const outcomes = decisions + (hasDefault ? 1 : 0);
            if (outcomes < 2) {
                problems.push({
                    id: idOf(node), name: flowNodeDisplayName(node),
                    reason: "分支节点至少需要两个结果（决策分支与默认分支合计 ≥ 2）"
                });
            }
            return true;
        });
        return problems;
    }

    function openValidateDialog(problems) {
        $("#validateSummary").textContent = `发现 ${problems.length} 处结构问题，修正后才能生效：`;
        $("#validateList").innerHTML = problems.map((problem) => `
            <li>
                <button type="button" class="validate-item" data-locate="${escapeText(problem.id)}">
                    <span class="validate-item-name">${escapeText(problem.name)}</span>
                    <span class="validate-item-reason">${escapeText(problem.reason)}</span>
                </button>
            </li>`).join("");
        const dialog = $("#validateDialog");
        if (!dialog.open) dialog.showModal();
    }

    function promoteToFormal() {
        if (placeholderGate()) return;
        const problems = collectViolations();
        if (problems.length === 0) { saveToBackend(true); return; }
        openValidateDialog(problems);
    }

    function revealNode(id) {
        const path = [];
        const locate = (node) => {
            path.push(node);
            if (idOf(node) === id) return true;
            for (const child of childNodes(node)) { if (locate(child)) return true; }
            path.pop();
            return false;
        };
        if (!locate(tree)) return;
        path.slice(0, -1).forEach((ancestor) => setCollapsed(ancestor, false));
        fitMode = false;
        render({ preserveView: true });
        selectNode(id);
        requestAnimationFrame(() => {
            const shell = treeRoot.querySelector(`[data-node-id="${id}"]`);
            if (!shell) return;
            const shellRect = shell.getBoundingClientRect();
            const viewRect = viewport.getBoundingClientRect();
            panX += (viewRect.left + viewRect.width / 2) - (shellRect.left + shellRect.width / 2);
            panY += (viewRect.top + viewRect.height / 2) - (shellRect.top + shellRect.height / 2);
            applyTransform();
        });
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
        if (reorderSuppress.flow) { reorderSuppress.flow = false; return; }
        const shell = event.target.closest(".node-shell");
        if (!shell) return;
        const nextId = shell.dataset.nodeId;
        if (nextId !== selectedId && !confirmDiscardInspectorEdit()) return;
        selectNode(nextId);
        const action = event.target.closest("[data-action]")?.dataset.action;
        if (action === "collapse") { toggleCollapse(); return; }
        // 待配置节点点击即进入配置：占位→配置弹窗；未配置判断→条件编辑弹窗。
        const node = findNode(nextId)?.node;
        if (node?.type === "PH") { openNodeConfig(node); return; }
        if (node?.type === "J" && isUnconfigured(node)) { openRuleDialog(nextId); return; }
    });
    treeRoot.addEventListener("dblclick", (event) => {
        const shell = event.target.closest(".node-shell");
        if (!shell) return;
        const node = findNode(shell.dataset.nodeId)?.node;
        if (node?.type === "J") openRuleDialog(shell.dataset.nodeId);
    });
    treeRoot.addEventListener("keydown", (event) => {
        if ((event.key === "Enter" || event.key === " ") && event.target.matches(".node-shell")) {
            event.preventDefault();
            const nextId = event.target.dataset.nodeId;
            if (nextId !== selectedId && !confirmDiscardInspectorEdit()) return;
            selectNode(nextId);
        }
    });

    // ---- 画布节点右键上下文菜单（与右栏同组操作，平时零干扰）----
    const CTX_ICONS = {
        add: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
        insert: '<svg viewBox="0 0 24 24"><path d="M12 19V5M5 12l7-7 7 7"/></svg>',
        rule: '<svg viewBox="0 0 24 24"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="2.5"/></svg>',
        config: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
        trash: '<svg viewBox="0 0 24 24"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/></svg>',
    };
    const contextMenu = document.getElementById("nodeContextMenu");
    function contextItemsFor(found) {
        const node = found.node;
        const items = [];
        if (node.type === "PH") {
            items.push({ label: node.slot === "decision" ? "配置条件" : "配置此节点", icon: "config", kind: "hot", onClick: () => openNodeConfig(node) });
        } else {
            flowNodeOperations(node).forEach((op, i) => {
                items.push({ label: op.label, icon: "add", kind: i === 0 ? "hot" : "normal", onClick: () => startAddConfig(op.relation) });
            });
            if (node.type === "J") items.push({ label: "查看规则", icon: "rule", kind: "normal", onClick: () => openRuleDialog(idOf(node)) });
            if (node.type !== "T") items.push({ label: "向前插入", icon: "insert", kind: "normal", onClick: () => openInsertPanel() });
        }
        if (node.type !== "T") {
            const canDel = canDeleteFlowNode(node, found.parent, found.relation);
            items.push({ sep: true });
            items.push({ label: "删除节点", icon: "trash", kind: "danger", disabled: !canDel, onClick: () => { deleteSelected(); } });
        }
        return items;
    }
    function hideContextMenu() { contextMenu.hidden = true; contextMenu.innerHTML = ""; contextMenu._items = null; }
    function openContextMenu(x, y, found) {
        const items = contextItemsFor(found);
        if (!items.length) { hideContextMenu(); return; }
        contextMenu.innerHTML = items.map((it, idx) => it.sep
            ? '<div class="ctx-sep"></div>'
            : `<button class="ctx-item ${it.kind === "hot" ? "hot" : it.kind === "danger" ? "danger" : ""}" type="button"${it.disabled ? " disabled" : ""} data-ctx="${idx}">${CTX_ICONS[it.icon] || ""}<span>${escapeText(it.label)}</span></button>`).join("");
        contextMenu._items = items;
        contextMenu.hidden = false;
        contextMenu.style.left = "0px"; contextMenu.style.top = "0px";
        const rect = contextMenu.getBoundingClientRect();
        const nx = Math.min(x, window.innerWidth - rect.width - 8);
        const ny = Math.min(y, window.innerHeight - rect.height - 8);
        contextMenu.style.left = `${Math.max(8, nx)}px`;
        contextMenu.style.top = `${Math.max(8, ny)}px`;
    }
    treeRoot.addEventListener("contextmenu", (event) => {
        const shell = event.target.closest(".node-shell");
        if (!shell) return;
        event.preventDefault();
        const id = shell.dataset.nodeId;
        if (id !== selectedId && !confirmDiscardInspectorEdit()) return;
        selectNode(id);
        const found = findNode(id);
        if (found) openContextMenu(event.clientX, event.clientY, found);
    });
    contextMenu.addEventListener("click", (event) => {
        const btn = event.target.closest("[data-ctx]");
        if (!btn) return;
        const item = contextMenu._items && contextMenu._items[Number(btn.dataset.ctx)];
        hideContextMenu();
        if (item && !item.disabled && item.onClick) item.onClick();
    });
    document.addEventListener("mousedown", (event) => {
        const t = event.target;
        if (!contextMenu.hidden && (!(t instanceof Element) || !t.closest("#nodeContextMenu"))) hideContextMenu();
    });
    document.addEventListener("contextmenu", (event) => {
        const t = event.target;
        if (!contextMenu.hidden && (!(t instanceof Element) || !t.closest(".node-shell"))) hideContextMenu();
    });
    document.addEventListener("keydown", (event) => { if (event.key === "Escape") hideContextMenu(); });
    viewport.addEventListener("wheel", hideContextMenu, { passive: true });
    viewport.addEventListener("scroll", hideContextMenu, true);

    ruleTreeRoot.addEventListener("click", (event) => {
        if (reorderSuppress.rule) { reorderSuppress.rule = false; return; }
        if (event.target.closest("#ruleAddRootButton")) { startRuleAdd(); return; }
        const shell = event.target.closest(".node-shell");
        if (!shell) {
            ruleSelectedId = null;
            hideRulePopover();
            renderRuleTree();
            updateRuleEditor();
            return;
        }
        ruleSelectedId = shell.dataset.ruleNodeId;
        hideRulePopover();
        renderRuleTree();
        updateRuleEditor();
        ruleTreeRoot.querySelector(`[data-rule-node-id="${ruleSelectedId}"]`)?.focus({ preventScroll: true });
    });
    ruleTreeRoot.addEventListener("keydown", (event) => {
        if ((event.key === "Enter" || event.key === " ") && event.target.matches(".node-shell")) {
            event.preventDefault();
            ruleSelectedId = event.target.dataset.ruleNodeId;
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
        if (event.target.closest("[data-config-ph]")) {
            const found = selectedId ? findNode(selectedId) : null;
            if (found && found.node.type === "PH") openNodeConfig(found.node);
            return;
        }
        if (event.target.closest("[data-insert-before]")) { openInsertPanel(); return; }
        const relation = event.target.closest("[data-flow-relation]")?.dataset.flowRelation;
        if (relation) startAddConfig(relation);
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
        walk(tree, (node) => { if (node.type !== "J" && childNodes(node).length) setCollapsed(node, true); });
        render({ preserveView: true });
    });
    $("#expandAllButton").addEventListener("click", () => {
        collapsedIds.clear();
        render({ preserveView: true });
    });
    function setInspectorCollapsed(collapsed) {
        document.querySelector(".workspace").classList.toggle("inspector-collapsed", collapsed);
        $("#inspectorExpand").hidden = !collapsed;
        if (fitMode) requestAnimationFrame(fitTree);
    }
    $("#inspectorCollapse").addEventListener("click", () => setInspectorCollapsed(true));
    $("#inspectorExpand").addEventListener("click", () => setInspectorCollapsed(false));
    $("#saveDraftButton").addEventListener("click", saveDraft);
    $("#promoteButton").addEventListener("click", promoteToFormal);
    $("#validateCloseButton").addEventListener("click", () => $("#validateDialog").close());
    $("#validateDismissButton").addEventListener("click", () => $("#validateDialog").close());
    $("#validateList").addEventListener("click", (event) => {
        const item = event.target.closest("[data-locate]");
        if (!item) return;
        $("#validateDialog").close();
        revealNode(item.dataset.locate);
    });
    $("#deleteButton").addEventListener("click", deleteSelected);
    $("#editNodeButton").addEventListener("click", () => {
        const found = selectedId ? findNode(selectedId) : null;
        if (found?.node.type === "J") openRuleDialog(selectedId);
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

    $("#ruleDialogCloseButton").addEventListener("click", () => { ruleDialogCommitted = true; ruleDialog.close(); });
    $("#ruleDialogCancelButton").addEventListener("click", () => { ruleDialogCommitted = false; ruleDialog.close(); });
    $("#ruleEditorDeleteButton").addEventListener("click", () => { deleteSelectedRule(); });
    $("#ruleAddCancelButton").addEventListener("click", cancelRuleAdd);
    $("#ruleAddConfirmButton").addEventListener("click", confirmRuleAdd);
    $("#ruleAddRowButton").addEventListener("click", appendRuleAddRow);
    $("#ruleAddRows").addEventListener("change", (event) => {
        const index = Number(event.target.dataset.ruleAddSelect);
        if (Number.isInteger(index) && ruleAddRuleIds[index] !== undefined) ruleAddRuleIds[index] = event.target.value;
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
        const target = node.type === "R" ? node : node.rules[idx];
        if (target && target.type === "R") {
            target.expr = select.value;
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
    $("#ruleNegateInput").addEventListener("change", (event) => {
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node) return;
        node.negative = event.target.checked;
        renderRuleTree();
        render({ preserveView: true });
    });
    ruleDialog.addEventListener("cancel", () => { ruleDialogCommitted = false; });
    ruleDialog.addEventListener("close", () => {
        const committed = ruleDialogCommitted;
        if (!ruleDialogCommitted && ruleDialogSnapshot && ruleJudgeId) {
            if (ruleDialogSnapshot.rollbackOnCancel) {
                ruleDialogSnapshot.rollbackOnCancel();
            } else {
                const found = findNode(ruleJudgeId);
                if (found) { found.node.rule = ruleDialogSnapshot.rule; found.node.action = ruleDialogSnapshot.action; }
            }
        }
        ruleDialogSnapshot = null;
        ruleDialogCommitted = false;
        ruleJudgeId = null;
        ruleSelectedId = null;
        hideRulePopover();
        render({ preserveView: true });
        if (committed) markDraft();
    });

    document.addEventListener("pointerdown", (event) => {
        if (event.target.closest(".node-shell")) return;
        document.activeElement?.closest?.(".node-shell")?.blur();
    }, true);

    $("#confirmAddButton").addEventListener("click", () => {
        // 添加面板现仅用于「向前插入（包裹为结构）」。
        if (insertBeforeMode) insertNodeBefore();
    });
    // 「配置节点」弹窗
    $("#nodeConfigType").addEventListener("change", syncNodeConfigRule);
    $("#nodeConfigConfirmButton").addEventListener("click", confirmNodeConfig);
    $("#nodeConfigCancelButton").addEventListener("click", closeNodeConfig);
    $("#nodeConfigCloseButton").addEventListener("click", closeNodeConfig);
    document.getElementById("nodeConfigDialog").addEventListener("cancel", (e) => { e.preventDefault(); closeNodeConfig(); });
    $("#newNodeCancelButton").addEventListener("click", closeAddPanel);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#inspectorAddPanel").hidden) closeAddPanel();
        const editing = event.target.matches("input, select, textarea");
        if (!editing && ruleDialog.open && (event.key === "Delete" || event.key === "Backspace")) deleteSelectedRule();
        if (!editing && event.key === "0") fitTree();
    });

    const reorderSuppress = { flow: false, rule: false };

    function createSiblingReorder(cfg) {
        const cont = cfg.container;
        const THRESHOLD = 5;
        let st = null;

        function members(parent, relation) {
            return relation === "rule" ? (parent.rules || []) : (parent.branches || []);
        }

        function startDrag() {
            st.dragging = true;
            const list = members(st.parent, st.relation);
            const others = list.filter((n) => n !== st.node);
            st.origIndex = list.indexOf(st.node);
            st.siblingEls = others
                .map((n) => cont.querySelector(`[${cfg.branchAttr}="${idOf(n)}"]`))
                .filter(Boolean);
            st.branchEl.classList.add("reorder-source");
            cont.classList.add("reorder-grabbing");
            const draggedCard = st.branchEl.querySelector(".node-card");
            st.cardW = draggedCard ? draggedCard.offsetWidth : 96;
            st.cardH = draggedCard ? draggedCard.offsetHeight : 40;
            const label = (cfg.labelOf ? cfg.labelOf(st.node) : "") || "节点";
            st.slot = document.createElement("span");
            st.slot.className = "reorder-slot";
            st.slot.textContent = label;
            st.container.appendChild(st.slot);
            st.chip = document.createElement("div");
            st.chip.className = "reorder-chip";
            st.chip.textContent = label;
            document.body.appendChild(st.chip);
            try { cont.setPointerCapture(st.pointerId); } catch (_) { /* ignore */ }
        }

        function updateDrag(event) {
            const els = st.siblingEls;
            const pointer = cfg.axis === "x" ? event.clientX : event.clientY;
            let k = els.length;
            for (let i = 0; i < els.length; i++) {
                const rect = els[i].getBoundingClientRect();
                const mid = cfg.axis === "x" ? rect.left + rect.width / 2 : rect.top + rect.height / 2;
                if (pointer < mid) { k = i; break; }
            }
            st.dropIndex = k;
            const noop = (k === st.origIndex);
            positionSlot(noop ? -1 : k);
            if (st.chip) {
                st.chip.style.left = `${event.clientX + 14}px`;
                st.chip.style.top = `${event.clientY + 14}px`;
                st.chip.classList.toggle("reorder-chip-muted", noop);
            }
        }

        function positionSlot(k) {
            const els = st.siblingEls;
            const slot = st.slot;
            if (!els.length || k < 0) { slot.style.display = "none"; return; }
            slot.style.display = "flex";
            slot.style.width = `${st.cardW}px`;
            slot.style.height = `${st.cardH}px`;
            const shell0 = els[0].querySelector(".node-shell");
            const cardSize = (el, prop) => {
                const card = el.querySelector(".node-card");
                return card ? card[prop] : (prop === "offsetWidth" ? st.cardW : st.cardH);
            };
            if (cfg.axis === "x") {
                const cardCenter = (el) => el.offsetLeft + el.offsetWidth / 2;
                const half = (el) => cardSize(el, "offsetWidth") / 2;
                const rowTop = els[0].offsetTop + (shell0 ? shell0.offsetTop : 0);
                let cx;
                if (k <= 0) cx = cardCenter(els[0]) - half(els[0]) - st.cardW / 2 - 14;
                else if (k >= els.length) {
                    const last = els[els.length - 1];
                    cx = cardCenter(last) + half(last) + st.cardW / 2 + 14;
                } else cx = ((cardCenter(els[k - 1]) + half(els[k - 1])) + (cardCenter(els[k]) - half(els[k]))) / 2;
                slot.style.left = `${cx - st.cardW / 2}px`;
                slot.style.top = `${rowTop}px`;
            } else {
                const cardCenter = (el) => el.offsetTop + el.offsetHeight / 2;
                const half = (el) => cardSize(el, "offsetHeight") / 2;
                const rowLeft = els[0].offsetLeft + (shell0 ? shell0.offsetLeft : 0);
                let cy;
                if (k <= 0) cy = cardCenter(els[0]) - half(els[0]) - st.cardH / 2 - 10;
                else if (k >= els.length) {
                    const last = els[els.length - 1];
                    cy = cardCenter(last) + half(last) + st.cardH / 2 + 10;
                } else cy = ((cardCenter(els[k - 1]) + half(els[k - 1])) + (cardCenter(els[k]) - half(els[k]))) / 2;
                slot.style.left = `${rowLeft}px`;
                slot.style.top = `${cy - st.cardH / 2}px`;
            }
        }

        function commit(ref) {
            const list = members(ref.parent, ref.relation);
            const dragged = ref.node;
            const others = list.filter((n) => n !== dragged);
            if (!list.includes(dragged)) return;
            const k = Math.max(0, Math.min(ref.dropIndex ?? others.length, others.length));
            const order = [...others.slice(0, k), dragged, ...others.slice(k)];
            list.length = 0;
            order.forEach((n) => list.push(n));
        }

        function finish(doCommit) {
            if (!st) return;
            const ref = st;
            st = null;
            if (ref.slot) ref.slot.remove();
            if (ref.chip) ref.chip.remove();
            ref.branchEl.classList.remove("reorder-source");
            cont.classList.remove("reorder-grabbing");
            try { cont.releasePointerCapture(ref.pointerId); } catch (_) { /* ignore */ }
            if (ref.dragging) reorderSuppress[cfg.key] = true;
            if (ref.dragging && doCommit && ref.dropIndex != null && ref.dropIndex !== ref.origIndex) {
                commit(ref);
                cfg.afterCommit(ref);
            }
        }

        cont.addEventListener("pointerdown", (event) => {
            if (event.button !== 0) return;
            reorderSuppress[cfg.key] = false;
            if (cfg.canStart && !cfg.canStart()) return;
            if (event.target.closest("button, select, input, [data-action]")) return;
            const shell = event.target.closest(".node-shell");
            if (!shell || !shell.dataset.reorderable) return;
            const id = shell.dataset[cfg.shellIdKey];
            const entry = cfg.findEntry(id);
            if (!entry || !entry.parent) return;
            const branchEl = cont.querySelector(`[${cfg.branchAttr}="${id}"]`);
            if (!branchEl || !branchEl.parentElement) return;
            st = {
                id, node: entry.node, parent: entry.parent, relation: cfg.relationOf(entry),
                startX: event.clientX, startY: event.clientY, pointerId: event.pointerId,
                branchEl, container: branchEl.parentElement,
                dragging: false, dropIndex: null, siblingEls: [],
                slot: null, chip: null, cardW: 0, cardH: 0
            };
        });
        cont.addEventListener("pointermove", (event) => {
            if (!st || event.pointerId !== st.pointerId) return;
            if (!st.dragging) {
                if (Math.hypot(event.clientX - st.startX, event.clientY - st.startY) < THRESHOLD) return;
                startDrag();
            }
            updateDrag(event);
        });
        cont.addEventListener("pointerup", () => finish(true));
        cont.addEventListener("pointercancel", () => finish(false));
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && st && st.dragging) finish(false);
        });
    }

    createSiblingReorder({
        container: treeRoot,
        key: "flow",
        axis: "x",
        branchAttr: "data-branch-id",
        shellIdKey: "nodeId",
        findEntry: (id) => findNode(id),
        relationOf: (entry) => entry.relation,
        labelOf: (node) => {
            const type = TYPES[node.type] || TYPES.A;
            if (node.type === "J") return ruleDisplayName(node);
            if (type.kind === "structure" || type.kind === "root") return compactStructureLabel(node, type);
            return flowNodeDisplayName(node);
        },
        afterCommit: (ref) => {
            selectedId = idOf(ref.node);
            inspectorEditingField = null;
            fitMode = false;
            render({ preserveView: true });
            markDraft();
        }
    });

    createSiblingReorder({
        container: ruleTreeRoot,
        key: "rule",
        axis: "y",
        branchAttr: "data-rule-branch-id",
        shellIdKey: "ruleNodeId",
        findEntry: (id) => findRuleNode(id),
        relationOf: () => "rule",
        labelOf: (node) => {
            const type = TYPES[node.type] || TYPES.R;
            return type.kind === "structure" ? compactStructureLabel(node, type) : ruleNodeDisplayName(node);
        },
        afterCommit: (ref) => {
            ruleSelectedId = idOf(ref.node);
            renderRuleTree();
            updateRuleEditor();
            render({ preserveView: true });
        }
    });

    new ResizeObserver(() => { if (fitMode) fitTree(); }).observe(viewport);
    render({ preserveView: false });
    updateDocStatus();

    // 对外只读桥（供后端接线层与基准/测试使用）。
    window.__mousikaEditor = {
        getTreeJson() { return T.serialize(tree); },
        loadTreeJson(json, opts) { loadTree(json, opts || {}); },
        setDocStatus(status) { docStatus = status === "formal" ? "formal" : "draft"; updateDocStatus(); },
        markDraft
    };

    function showLoadError(message) {
        $("#loadErrorText").textContent = message;
        $("#loadErrorBanner").hidden = false;
    }

    $("#loadErrorRetry").addEventListener("click", () => window.location.reload());

    // ---- 后端接线：规则下拉来自轻量引用接口；?flowId 打开真实规则流程 ----
    (async function initBackend() {
        if (!window.MousikaApi) return;
        const flowId = resolveFlowId();
        // 无 flowId = 独立演示，保留内置示例与演示规则定义，不触碰后端。
        if (!flowId) return;
        try {
            const all = await window.MousikaApi.listRuleReferences();
            applyRuleDefinitions(all);
            $("#loadErrorBanner").hidden = true;
            render({ preserveView: true });
        } catch (e) {
            console.error("加载规则失败", e);
            showLoadError(`规则加载失败：${e.message}。规则选择暂不可用。`);
        }

        try {
            const flow = await window.MousikaApi.getFlow(flowId);
            if (!flow) throw new Error("场景不存在");
            flowMeta = { id: flow.id, name: flow.name, description: flow.description, version: flow.version };
            const titleEl = document.querySelector(".canvas-titlebar strong");
            if (titleEl) titleEl.textContent = flow.name || "流程画布";
            const crumbEl = document.getElementById("flowCrumbName");
            if (crumbEl) crumbEl.textContent = flow.name || "规则树编辑器";
            document.title = `${flow.name || "场景"} · Mousika`;
            loadTree(flow.ruleTree);
            docStatus = flow.status === 1 ? "formal" : flow.status === 2 ? "disabled" : "draft";
            dirty = false;
            updateDocStatus();
        } catch (e) {
            console.error("加载场景失败", e);
            // 只读错误态：禁用保存/生效，避免把空白画布当成真实流程覆盖后端。
            flowLoadFailed = true;
            setSaveBusy(false);
            showLoadError(`场景加载失败：${e.message}`);
            const titleEl = document.querySelector(".canvas-titlebar strong");
            if (titleEl) titleEl.textContent = "加载失败";
            const crumbEl = document.getElementById("flowCrumbName");
            if (crumbEl) crumbEl.textContent = "加载失败";
            openConfirm(`加载场景失败：${e.message}`, { title: "加载失败", confirmLabel: "知道了" });
        }
    })();

    window.addEventListener("beforeunload", (event) => {
        if (!dirty || !flowMeta) return;
        event.preventDefault();
        event.returnValue = "";
    });
    const backToFlows = $("#backToFlows");
    if (backToFlows) {
        backToFlows.addEventListener("click", (event) => {
            if (dirty && !window.confirm("当前修改尚未保存，确定返回场景列表吗？")) {
                event.preventDefault();
            }
        });
    }

    if (new URLSearchParams(location.search).has("bench")) {
        window.__mousikaBench = {
            sampleTree,
            setTree(newTree) { loadTree(newTree, { bypassLimits: true }); },
            loadTree(newTree, opts) { loadTree(newTree, opts); },
            limits: TREE_LIMITS,
            assertWithinLimits(root) { return assertTreeWithinLimits(root); },
            render(opts) { render(opts || { preserveView: true }); },
            metrics() {
                const stats = canvasStats(tree);
                return {
                    width: treeRoot.offsetWidth,
                    height: treeRoot.offsetHeight,
                    flowCount: stats.flowNodes,
                    ruleCount: stats.modalRuleNodes
                };
            },
            findNode,
            openRuleDialog(judgeId) { openRuleDialog(judgeId); },
            closeRuleDialog() { if (ruleDialog.open) ruleDialog.close(); },
            renderRuleTree() { renderRuleTree(); },
            ruleTreeRoot() { return ruleTreeRoot; },
            fitTree() { fitTree(); },
            getTree() { return tree; },
            treeRoot() { return treeRoot; },
            viewport() { return viewport; }
        };
    }
})();
