(() => {
    "use strict";

    // 画布页必须依附一条规则流程。通过 HTTP 访问且无 flowId（bench 基准除外）时，
    // 回到场景列表，避免正式服务里裸开画布展示内置演示假数据造成困惑。
    // flowId 取自路径 /flow/{flowId}（正式路由）或查询串 ?flowId=（向后兼容）。
    function resolveFlowId() {
        const m = location.pathname.match(/\/flow\/(f[1-9]\d*)/);
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
    const T = window.MosikaTree;
    const D = window.MosikaFlowDesign;
    const { childEdges, childNodes, executionEdges, make } = T;

    const TYPES = D.CAPABILITIES;

    // 规则定义：默认演示假数据，接入后端后由 /api/rules 覆盖（见文件末尾接线层）。
    // expr 只保存稳定的 ruleId 引用；desc 供展示。动作与条件同源于规则池。
    let RULE_DEFINITIONS = Array.from({ length: 12 }, (_, index) => ({
        ruleId: `c${index + 1}`, desc: `业务判断条件${index + 1}`, kind: "condition"
    }));
    let ACTION_DEFINITIONS = Array.from({ length: 13 }, (_, index) => ({
        ruleId: `a${index + 1}`, desc: `业务操作${index + 1}`, kind: "action"
    }));
    let RULE_DEFINITION_BY_ID = new Map(RULE_DEFINITIONS.map((d) => [d.ruleId, d]));
    let ACTION_DEFINITION_BY_ID = new Map(ACTION_DEFINITIONS.map((d) => [d.ruleId, d]));

    // 接入后端时用真实 AtomicRule/RuleFlow 引用覆盖演示数据，并按 kind 拆分：
    // 判断条件(condition) 供判断/条件节点引用；执行动作(action) 供动作节点引用。
    function applyRuleDefinitions(list, flows) {
        const toDef = (r) => ({
            ruleId: String(r.ruleId),
            desc: r.name || r.description || String(r.ruleId),
            kind: r.kind === "action" ? "action" : "condition",
            expression: r.expression || "",
            // 模板参数定义，供画布在引用该规则的节点上渲染 $args 录入表单；缺省为无参
            params: Array.isArray(r.params) ? r.params : []
        });
        const defs = (list || []).map(toDef);
        RULE_DEFINITIONS = defs.filter((d) => d.kind !== "action");
        ACTION_DEFINITIONS = defs.filter((d) => d.kind === "action")
            .concat((flows || []).map((flow) => ({
                ruleId: String(flow.flowId),
                desc: flow.name || flow.description || String(flow.flowId),
                kind: "action",
                composite: true
            })));
        RULE_DEFINITION_BY_ID = new Map(RULE_DEFINITIONS.map((d) => [d.ruleId, d]));
        ACTION_DEFINITION_BY_ID = new Map(ACTION_DEFINITIONS.map((d) => [d.ruleId, d]));
    }

    // 与 docs/images/ui-tree.svg 中的示例逐节点对应，用 TreeNode 语义模型重建。
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
        const j8 = make.condition(l1);
        const s14 = make.serial();
        const d15 = make.decision();
        const j16 = make.condition(make.atom("c1")); j16.next = make.action("a1");
        const l18 = make.logic("&&"); l18.name = "业务复合规则2"; l18.rules.push(make.atom("c2"), make.atom("c3"));
        const j18 = make.condition(l18); j18.next = make.action("a3");
        d15.branches.push(j16, j18);
        s14.branches.push(d15);
        const j23 = make.condition(make.atom("c4")); j23.next = make.action("a4");
        s14.branches.push(j23);
        j8.next = s14;

        const j25 = make.condition(make.atom("c5"));
        const d26 = make.decision();
        const j27 = make.condition(make.atom("c6")); j27.next = make.action("a6");
        const j29 = make.condition(make.atom("c7")); j29.next = make.action("a7");
        d26.branches.push(j27, j29);
        d26.defaultBranch = make.action("a5");
        j25.next = d26;

        const s32 = make.serial();
        const j33 = make.condition(make.atom("c8")); j33.next = make.action("a8");
        const j35 = make.condition(make.atom("c9")); j35.next = make.action("a9");
        s32.branches.push(j33, j35);

        d.branches.push(j8, j25);
        d.defaultBranch = s32;
        s.branches.push(d);
        return root;
    };

    // ---- 语义树 + 旁路编辑器状态（选中/折叠/临时 id 不进语义树）----
    // 真实流程（带 flowId）初始为空白根，等待后端加载替换，避免把内置演示假数据当成真实流程闪现；
    // 独立演示 / bench 才用 sampleTree。
    const emptyTree = () => make.root();
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
    let ruleJudgeDraft = null;
    let ruleDraftCommit = null;
    let ruleSelectedId = null;
    // 后端接线：当前打开的 flow 元数据（null=独立演示，无法保存）。
    let flowMeta = null;
    let flowVersions = [];
    let savingFlow = false;
    let pendingFlowAction = null;
    let switchingVersion = false;
    let editorReadOnly = false;
    let dirty = false;
    let testPanelOpen = false;
    let testRunning = false;
    const executedPaths = new Set();

    const FLOW_TYPES = D.FLOW_TYPES;

    const TREE_LIMITS = { maxDepth: 128, maxNodes: 2000 };

    const $ = window.MosikaUi.$;
    const escapeText = window.MosikaUi.escapeHtml;
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

    function openConfirm(message, {
        title = "确认操作",
        confirmLabel = "确定",
        alternateLabel = null
    } = {}) {
        const choiceMode = Boolean(alternateLabel);
        $("#confirmDialogTitle").textContent = title;
        const messageElement = $("#confirmDialogMessage");
        messageElement.textContent = message;
        messageElement.hidden = choiceMode;
        $("#confirmDialogConfirm").textContent = confirmLabel;
        $("#confirmDialogChoices").hidden = !choiceMode;
        $("#confirmDialogActions").hidden = choiceMode;
        $("#confirmDialogAlternateTitle").textContent = alternateLabel || "";
        $("#confirmDialogChoiceConfirmTitle").textContent = confirmLabel;
        confirmDialog.classList.toggle("choice-dialog", choiceMode);
        return new Promise((resolve) => {
            const onClose = () => {
                confirmDialog.removeEventListener("close", onClose);
                resolve(confirmDialog.returnValue === "alternate"
                    ? "alternate"
                    : confirmDialog.returnValue === "default");
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

    // 条件节点和循环节点都拥有独立的条件规则子树
    function isConditionOwner(node) {
        return D.isConditionOwner(node);
    }

    function conditionRule(node) {
        return isConditionOwner(node) ? node.rule || null : null;
    }

    function setConditionRule(node, rule) {
        node.rule = rule;
    }

    function countNodes(node) {
        return 1 + childNodes(node).reduce((sum, child) => sum + countNodes(child), 0);
    }

    function countFlowNodes(node, includeNaturalNext = true) {
        const children = childEdges(node).filter((edge) => edge.relation !== "rule"
            && (includeNaturalNext || edge.relation !== "next"));
        return 1 + children.reduce((sum, edge) => sum + countFlowNodes(edge.node), 0);
    }

    // ---- 名称解析：所有 UITree 节点统一使用 name ----
    function flowNodeName(node) {
        return node.name?.trim() || "";
    }

    function ruleDisplayName(node) {
        const rule = conditionRule(node);
        if (!rule) return "未配置规则";
        return ruleNodeDisplayName(rule);
    }

    function ruleNodeDisplayName(node) {
        const name = node.name?.trim();
        if (name) return name;
        if (node.type === "BooleanNode") {
            return ruleDefinitionById(node.expr)?.desc || "未命名规则";
        }
        return D.isCompositeRule(node) ? "复合规则" : TYPES[node.type]?.name || "未命名规则";
    }

    function isCompositeReference(node) {
        return node.type === "ActionNode" && /^f[1-9]\d*$/.test(String(node.rule?.expr || ""));
    }

    function flowNodeDisplayName(node) {
        if (node.type === "TreeNode") return "开始";
        const name = flowNodeName(node);
        if (name) return name;
        if (node.type === "ConditionNode") return ruleDisplayName(node);
        if (node.type === "ActionNode") {
            const ruleId = node.rule?.expr;
            if (isCompositeReference(node)) return `复合规则 ${ruleId}`;
            return actionDefinitionById(ruleId)?.desc || TYPES[node.type].name;
        }
        return TYPES[node.type]?.name || "未命名节点";
    }

    function flowNodeCanvasLabel(node) {
        const type = TYPES[node.type] || TYPES.ActionNode;
        return D.usesCompactCanvasLabel(node) ? compactNodeLabel(node, type) : flowNodeDisplayName(node);
    }

    function flowNodeCanvasTitle(node, label) {
        const type = TYPES[node.type] || TYPES.ActionNode;
        if (node.type === "TreeNode") return type.name;
        if (node.type === "LoopNode") {
            const name = flowNodeName(node);
            return `${type.name}${name ? ` · ${name}` : ""} · 最多 ${node.max} 次 · ${ruleDisplayName(node)}`;
        }
        if (D.usesCompactCanvasLabel(node)) {
            const name = flowNodeName(node);
            return name ? `${type.name} · ${name}` : type.name;
        }
        return `${type.name} · ${label}`;
    }

    function ruleNodeCanvasLabel(node) {
        const type = TYPES[node.type] || TYPES.BooleanNode;
        return type.kind === "structure" ? compactNodeLabel(node, type) : ruleNodeDisplayName(node);
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
            for (const edge of childEdges(node)) stack.push([edge.node, depth + 1]);
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
        const semantic = T.deserialize(root);
        if (!bypassLimits) assertTreeWithinLimits(semantic, "导入的树");
        tree = semantic;
        executedPaths.clear();
        $("#testResult").hidden = true;
        collapsedIds.clear();
        selectedId = null;
        ruleSelectedId = null;
        ruleJudgeId = null;
        ruleJudgeDraft = null;
        ruleDraftCommit = null;
        inspectorEditingField = null;
        docStatus = "draft";
        dirty = false;
        syncFlowHeader();
        render({ preserveView: false });
    }

    function depthOf(targetId) {
        const stack = [[tree, 1]];
        while (stack.length) {
            const [node, depth] = stack.pop();
            if (idOf(node) === targetId) return depth;
            for (const edge of childEdges(node)) stack.push([edge.node, depth + 1]);
        }
        return -1;
    }

    function limitBlockReason(parentNode, addedRoot, replacedNodes = 0) {
        const cur = inspectTree(tree);
        const add = inspectTree(addedRoot);
        const projectedNodes = cur.nodes - replacedNodes + add.nodes;
        if (projectedNodes > TREE_LIMITS.maxNodes) {
            return `节点总数将达 ${projectedNodes}，超过编辑器上限 ${TREE_LIMITS.maxNodes}`;
        }
        const parentDepth = parentNode ? depthOf(idOf(parentNode)) : 0;
        if (parentDepth > 0 && parentDepth + add.depth > TREE_LIMITS.maxDepth) {
            return `插入后嵌套深度将达 ${parentDepth + add.depth}，超过编辑器上限 ${TREE_LIMITS.maxDepth}`;
        }
        return null;
    }

    function availableRelations(parent) {
        return D.availableRelations(parent);
    }

    function flowNodeOperations(node) {
        return D.operations(node);
    }

    function canDeleteFlowNode(node, parent, relation) {
        return Boolean(parent);
    }

    function canDeleteCurrentOnly(node, parent, relation) {
        return Boolean(parent && node?.next && D.isPassThrough(node)
            && D.relationAllows(parent, relation, node.next.type));
    }

    // 从父节点上按 relation 移除某个子节点
    function detachChild(parent, node, relation) {
        const edge = childEdges(parent).find((candidate) => candidate.node === node && candidate.relation === relation);
        if (!edge) return;
        if (relation === "branch") {
            if (edge.kind === "list") parent.branches.splice(edge.index, 1);
            else parent[edge.field] = null;
        } else if (relation === "decision") {
            const idx = parent.branches.indexOf(node);
            if (idx >= 0) parent.branches.splice(idx, 1);
        } else if (relation === "next") {
            parent.next = null;
        } else if (relation === "default") {
            parent.defaultBranch = null;
        }
    }

    function compactNodeLabel(node, type) {
        return node.type === "LogicNode" && node.expr === "||" ? "或" : type.short;
    }

    function matchRequirementLabel(node) {
        if (node.minHits == null && node.maxHits == null) return "未设置";
        if (node.minHits == null) return `至多 ${node.maxHits} 个`;
        if (node.maxHits == null) return `至少 ${node.minHits} 个`;
        if (node.minHits === node.maxHits) return `恰好 ${node.minHits} 个`;
        return `${node.minHits}～${node.maxHits} 个`;
    }

    // ---- 主画布渲染 ----
    function flowChildPath(parentPath, edge) {
        if (edge.relation === "next") return `${parentPath}.next`;
        if (edge.relation === "branch" || edge.relation === "decision") {
            return `${parentPath}.branches[${edge.index}]`;
        }
        if (edge.relation === "default") return `${parentPath}.defaultBranch`;
        return parentPath;
    }

    function requiredFlowRelations(node) {
        return D.requiredRelations(node);
    }

    function renderFlowAddActions(node) {
        if (editorReadOnly) return "";
        const parentId = idOf(node);
        const operations = flowNodeOperations(node);
        return requiredFlowRelations(node).map((relation) => {
            const operation = operations.find((candidate) => candidate.relation === relation);
            if (!operation) return "";
            return `
            <div class="tree-branch tree-add-branch">
                <button class="tree-add-button" type="button"
                        data-add-parent-id="${parentId}" data-flow-relation="${operation.relation}">
                    ＋ ${escapeText(operation.label)}
                </button>
            </div>`;
        }).join("");
    }

    function renderBranch(node, parent = null, relation = null, siblingCount = 1, path = "$") {
        const type = TYPES[node.type] || TYPES.ActionNode;
        const reorderable = Boolean(parent && ["branch", "decision"].includes(relation)
            && siblingCount >= 2);
        const expressionValue = node.type === "ActionNode" ? node.rule?.expr : "";
        const expression = expressionValue ? `<span class="node-expression">${escapeText(expressionValue)}</span>` : "";
        const executionChildren = executionEdges(node);
        const collapsible = executionChildren.length > 0;
        const collapsed = Boolean(collapsible && isCollapsed(node));
        const visibleEdges = collapsed ? [] : executionChildren;
        const relationCounts = visibleEdges.reduce((counts, edge) => {
            counts.set(edge.relation, (counts.get(edge.relation) || 0) + 1);
            return counts;
        }, new Map());
        const renderedChildren = visibleEdges.map((edge) => renderBranch(
            edge.node, node, edge.relation, relationCounts.get(edge.relation),
            flowChildPath(path, edge))).join("");
        const addActions = collapsed ? "" : renderFlowAddActions(node);
        const childContent = node.type === "DecisionNode" && node.branches.length === 0
            ? `${addActions}${renderedChildren}`
            : `${renderedChildren}${addActions}`;
        const children = childContent ? `<div class="tree-children">${childContent}</div>` : "";
        const hiddenCount = executionChildren.length;
        const collapsedBadge = collapsed ? `<span class="collapsed-count" title="${hiddenCount} 个分支">${hiddenCount}</span>` : "";
        const title = flowNodeCanvasLabel(node);
        const cardKind = `${type.kind}${node.type === "ConditionNode" ? " judge-summary" : ""} node-type-${node.type.toLowerCase()}`;
        const cardTitle = flowNodeCanvasTitle(node, title);
        const id = idOf(node);
        const nodeShell = `
            <div class="node-shell${collapsible ? " has-fold-toggle" : ""}${executedPaths.has(path) ? " trace-node" : ""}"${reorderable ? ' data-reorderable="1"' : ""} data-node-id="${id}" data-flow-path="${escapeText(path)}" tabindex="0" role="treeitem" aria-selected="${selectedId === id}" aria-expanded="${!collapsed}">
                    <div class="node-card ${cardKind}" title="${escapeText(cardTitle)}">
                        <span class="node-title">${escapeText(title || type.short)}</span>
                        ${expression}
                    </div>
                    ${collapsible ? `<button class="node-fold-toggle" type="button" data-action="collapse" title="${collapsed ? "展开分支" : "折叠分支"}" aria-label="${collapsed ? "展开" : "收起"}">${collapsed ? "+" : "−"}</button>` : ""}
                    ${collapsedBadge}
                </div>`;
        return `
            <div class="tree-branch" data-branch-id="${id}" data-flow-path="${escapeText(path)}">
                ${nodeShell}${children}
            </div>`;
    }

    function flowPathMap() {
        const paths = new Map();
        const visit = (node, path) => {
            paths.set(path, node);
            executionEdges(node).forEach((edge) => visit(edge.node, flowChildPath(path, edge)));
        };
        visit(tree, "$");
        return paths;
    }

    function drawExecutionTrace() {
        treeRoot.querySelector("#executionTrace")?.remove();
        if (executedPaths.size < 2) return;
        const rootRect = treeRoot.getBoundingClientRect();
        if (!rootRect.width || !rootRect.height) return;
        const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
        svg.id = "executionTrace";
        svg.setAttribute("viewBox", `0 0 ${rootRect.width / scale} ${rootRect.height / scale}`);
        svg.setAttribute("aria-hidden", "true");
        const defs = document.createElementNS("http://www.w3.org/2000/svg", "defs");
        const gradient = document.createElementNS("http://www.w3.org/2000/svg", "linearGradient");
        gradient.id = "executionTraceGradient";
        gradient.setAttribute("gradientUnits", "userSpaceOnUse");
        gradient.setAttribute("x1", "0");
        gradient.setAttribute("y1", "0");
        gradient.setAttribute("x2", "120");
        gradient.setAttribute("y2", "120");
        gradient.setAttribute("spreadMethod", "repeat");
        [
            ["0%", "#ef4444"],
            ["10%", "#f97316"],
            ["20%", "#facc15"],
            ["30%", "#84cc16"],
            ["40%", "#22c55e"],
            ["50%", "#06b6d4"],
            ["60%", "#2563eb"],
            ["70%", "#06b6d4"],
            ["80%", "#22c55e"],
            ["90%", "#facc15"],
            ["100%", "#ef4444"]
        ].forEach(([offset, color]) => {
            const stop = document.createElementNS("http://www.w3.org/2000/svg", "stop");
            stop.setAttribute("offset", offset);
            stop.setAttribute("stop-color", color);
            gradient.appendChild(stop);
        });
        if (!window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            const animation = document.createElementNS("http://www.w3.org/2000/svg", "animateTransform");
            animation.setAttribute("attributeName", "gradientTransform");
            animation.setAttribute("type", "translate");
            animation.setAttribute("from", "-120 -120");
            animation.setAttribute("to", "0 0");
            animation.setAttribute("dur", "2.4s");
            animation.setAttribute("repeatCount", "indefinite");
            gradient.appendChild(animation);
        }
        defs.appendChild(gradient);
        svg.appendChild(defs);
        const localRect = (element) => {
            const rect = element.getBoundingClientRect();
            return {
                left: (rect.left - rootRect.left) / scale,
                top: (rect.top - rootRect.top) / scale,
                width: rect.width / scale,
                height: rect.height / scale
            };
        };
        const paths = flowPathMap();
        const connectorOffset = 0.75;
        paths.forEach((node, parentPath) => {
            if (!executedPaths.has(parentPath)) return;
            const parent = treeRoot.querySelector(`.node-shell[data-flow-path="${CSS.escape(parentPath)}"]`);
            if (!parent) return;
            executionEdges(node).forEach((edge) => {
                const childPath = flowChildPath(parentPath, edge);
                if (!executedPaths.has(childPath)) return;
                const child = treeRoot.querySelector(`.node-shell[data-flow-path="${CSS.escape(childPath)}"]`);
                if (!child) return;
                const from = localRect(parent.querySelector(".node-card") || parent);
                const to = localRect(child.querySelector(".node-card") || child);
                const startX = from.left + from.width / 2 + connectorOffset;
                const startY = from.top + from.height;
                const endY = to.top;
                const childBranch = child.closest(".tree-branch");
                const siblingBranches = Array.from(childBranch.parentElement.children)
                    .filter((element) => element.classList.contains("tree-branch"));
                const isFirstBranch = siblingBranches.length > 1 && childBranch === siblingBranches[0];
                const isLastBranch = siblingBranches.length > 1 && childBranch === siblingBranches[siblingBranches.length - 1];
                const endX = to.left + to.width / 2 + (isLastBranch ? -connectorOffset : connectorOffset);
                const turnY = localRect(childBranch).top + connectorOffset;
                const isOuterBranch = isFirstBranch || isLastBranch;
                const cornerRadius = isOuterBranch
                    ? Math.min(8 - connectorOffset, Math.abs(endX - startX), endY - turnY)
                    : 0;
                const line = document.createElementNS("http://www.w3.org/2000/svg", "path");
                if (cornerRadius > 0) {
                    const approachX = endX + Math.sign(startX - endX) * cornerRadius;
                    line.setAttribute("d", `M ${startX} ${startY} V ${turnY} H ${approachX} Q ${endX} ${turnY} ${endX} ${turnY + cornerRadius} V ${endY}`);
                } else {
                    line.setAttribute("d", `M ${startX} ${startY} V ${turnY} H ${endX} V ${endY}`);
                }
                line.setAttribute("stroke", "url(#executionTraceGradient)");
                svg.appendChild(line);
            });
        });
        treeRoot.appendChild(svg);
    }

    function render({ preserveView = true } = {}) {
        treeRoot.innerHTML = renderBranch(tree);
        updateFoldAllButton();
        updateInspector();
        syncReadonlyControls();
        requestAnimationFrame(() => {
            drawExecutionTrace();
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

    function definitionChoices(definitions, selectedRuleId, fallbackLabel = "") {
        if (!selectedRuleId || definitions.some((definition) => definition.ruleId === selectedRuleId)) {
            return definitions;
        }
        return [{ ruleId: selectedRuleId, desc: fallbackLabel || selectedRuleId }, ...definitions];
    }

    function definitionLabel(definition) {
        return `${definition.desc} · ${definition.ruleId}`;
    }

    function definitionOptionsHtml(definitions, selectedRuleId, menuId) {
        if (!definitions.length) return `<div class="rule-picker-empty">没有匹配的规则</div>`;
        return definitions.map((definition, index) => `
            <button class="rule-picker-option" id="${menuId}-option-${index}" type="button" role="option"
                    tabindex="-1" data-rule-id="${escapeText(definition.ruleId)}"
                    data-rule-label="${escapeText(definitionLabel(definition))}"
                    aria-selected="${definition.ruleId === selectedRuleId}">
                <span>${escapeText(definition.desc)}</span>
                <code>${escapeText(definition.ruleId)}</code>
            </button>`).join("");
    }

    function definitionPickerHtml(idPrefix, kind, definitions, selectedRuleId = "", fallbackLabel = "", valueAttributes = "") {
        const options = definitionChoices(definitions, selectedRuleId, fallbackLabel);
        const menuId = `${idPrefix}Options`;
        return `<div class="rule-picker" data-definition-kind="${kind}">
            <input class="rule-picker-filter" id="${idPrefix}Filter" type="text"
                   autocomplete="off" placeholder="输入规则名称或 ID"
                   value="${escapeText(definitionReference(options, selectedRuleId, fallbackLabel))}"
                   data-fallback-label="${escapeText(fallbackLabel)}"
                   role="combobox" aria-autocomplete="list" aria-expanded="false"
                   aria-controls="${menuId}" aria-label="选择引用规则">
            <input id="${idPrefix}Select" type="hidden" data-rule-picker-value ${valueAttributes}
                   value="${escapeText(selectedRuleId || "")}">
            <div class="rule-picker-menu" id="${menuId}" role="listbox" hidden>
                ${definitionOptionsHtml(options, selectedRuleId, menuId)}
            </div>
        </div>`;
    }

    function definitionPickerParts(filterInput) {
        const picker = filterInput.closest(".rule-picker");
        return {
            picker,
            valueInput: picker?.querySelector("[data-rule-picker-value]"),
            menu: picker?.querySelector(".rule-picker-menu")
        };
    }

    function definitionsForPicker(filterInput) {
        const { picker, valueInput } = definitionPickerParts(filterInput);
        if (!picker || !valueInput) return [];
        const definitions = picker.dataset.definitionKind === "rule" ? RULE_DEFINITIONS : ACTION_DEFINITIONS;
        return definitionChoices(definitions, valueInput.value, filterInput.dataset.fallbackLabel || valueInput.value);
    }

    function setDefinitionPickerActive(filterInput, index) {
        const { picker, menu } = definitionPickerParts(filterInput);
        const options = [...(menu?.querySelectorAll(".rule-picker-option") || [])];
        if (!picker || !options.length) {
            filterInput.removeAttribute("aria-activedescendant");
            return;
        }
        const nextIndex = Math.max(0, Math.min(index, options.length - 1));
        picker.dataset.activeIndex = String(nextIndex);
        options.forEach((option, optionIndex) => option.classList.toggle("is-active", optionIndex === nextIndex));
        filterInput.setAttribute("aria-activedescendant", options[nextIndex].id);
        options[nextIndex].scrollIntoView({ block: "nearest" });
    }

    function renderDefinitionPicker(filterInput, definitions, query = "", open = true) {
        const { picker, valueInput, menu } = definitionPickerParts(filterInput);
        if (!picker || !valueInput || !menu) return;
        const normalized = query.trim().toLowerCase();
        const filtered = query
            ? definitions.filter((definition) =>
                `${definition.desc} ${definition.ruleId}`.toLowerCase().includes(normalized))
            : definitions;
        menu.innerHTML = definitionOptionsHtml(filtered, valueInput.value, menu.id);
        menu.hidden = !open;
        filterInput.setAttribute("aria-expanded", String(open));
        const selectedIndex = filtered.findIndex((definition) => definition.ruleId === valueInput.value);
        picker.dataset.activeIndex = String(selectedIndex >= 0 ? selectedIndex : 0);
        if (open) setDefinitionPickerActive(filterInput, selectedIndex >= 0 ? selectedIndex : 0);
        else filterInput.removeAttribute("aria-activedescendant");
    }

    function syncDefinitionPicker(filterInput, valueInput, definitions, selectedRuleId = "") {
        const selected = definitions.find((definition) => definition.ruleId === selectedRuleId)
            || definitions[0]
            || null;
        valueInput.value = selected?.ruleId || "";
        filterInput.value = selected ? definitionLabel(selected) : "";
        filterInput.disabled = !definitions.length;
        renderDefinitionPicker(filterInput, definitions, "", false);
    }

    function closeDefinitionPicker(filterInput) {
        const { menu } = definitionPickerParts(filterInput);
        if (!menu) return;
        menu.hidden = true;
        filterInput.setAttribute("aria-expanded", "false");
        filterInput.removeAttribute("aria-activedescendant");
    }

    function chooseDefinitionOption(option) {
        const picker = option.closest(".rule-picker");
        const filterInput = picker?.querySelector(".rule-picker-filter");
        const valueInput = picker?.querySelector("[data-rule-picker-value]");
        if (!filterInput || !valueInput) return;
        valueInput.value = option.dataset.ruleId || "";
        filterInput.value = option.dataset.ruleLabel || "";
        valueInput.dispatchEvent(new Event("change", { bubbles: true }));
        if (filterInput.isConnected) filterInput.focus();
        closeDefinitionPicker(filterInput);
    }

    function filterDefinitionPicker(filterInput) {
        const { valueInput } = definitionPickerParts(filterInput);
        if (!valueInput) return;
        valueInput.value = "";
        renderDefinitionPicker(filterInput, definitionsForPicker(filterInput), filterInput.value, true);
    }

    function handleDefinitionPickerKeydown(event) {
        const filterInput = event.target;
        if (!filterInput.classList.contains("rule-picker-filter")) return false;
        const { picker, menu } = definitionPickerParts(filterInput);
        if (!picker || !menu) return false;
        if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            if (menu.hidden) renderDefinitionPicker(filterInput, definitionsForPicker(filterInput), "", true);
            const options = menu.querySelectorAll(".rule-picker-option");
            if (!options.length) return true;
            const currentIndex = Number(picker.dataset.activeIndex || 0);
            const offset = event.key === "ArrowDown" ? 1 : -1;
            setDefinitionPickerActive(filterInput, (currentIndex + offset + options.length) % options.length);
            return true;
        }
        if (event.key === "Enter" && !menu.hidden) {
            event.preventDefault();
            const options = menu.querySelectorAll(".rule-picker-option");
            const active = options[Number(picker.dataset.activeIndex || 0)];
            if (active) chooseDefinitionOption(active);
            return true;
        }
        if (event.key === "Escape" && !menu.hidden) {
            event.preventDefault();
            closeDefinitionPicker(filterInput);
            return true;
        }
        return false;
    }

    function judgeRuleReference(node) {
        const rule = conditionRule(node);
        if (!rule) return "未设置";
        if (rule.type === "BooleanNode") return definitionReference(RULE_DEFINITIONS, rule.expr);
        return "复合规则";
    }

    function judgeRuleReferenceMarkup(node) {
        const rule = conditionRule(node);
        const negateBadge = rule?.type === "BooleanNode" && rule.negative
            ? `<span class="detail-negate-badge" title="取反">非</span>`
            : "";
        return `${negateBadge}<span>${escapeText(judgeRuleReference(node))}</span>`;
    }

    function inspectorEditableType(type) {
        return FLOW_TYPES.includes(type);
    }

    // ---- 模板参数（$args）录入 ----
    // 承载 args 的宿主节点是流程节点内嵌的原子规则，规则编辑器中的原子节点则直接作为宿主
    function argsHost(node) {
        if (!node) return null;
        if (node.type === "ActionNode") return !isCompositeReference(node) && node.rule?.type === "AtomicNode" ? node.rule : null;
        if (T.hasArgs(node)) return node;
        if (isConditionOwner(node)) { const r = conditionRule(node); return r?.type === "BooleanNode" ? r : null; }
        return null;
    }

    function nodeReferencedRuleId(node) {
        return argsHost(node)?.expr || null;
    }

    // 节点引用规则声明的模板参数定义（数组），无则空。
    function nodeParamDefs(node) {
        const ruleId = nodeReferencedRuleId(node);
        if (!ruleId) return [];
        const def = ruleDefinitionById(ruleId) || actionDefinitionById(ruleId);
        return def && Array.isArray(def.params) ? def.params : [];
    }

    // 解析宿主节点当前 args（JSON对象文本）为对象；非对象归一为空。
    function parseArgs(host) {
        if (!host || !host.args) return {};
        try {
            const v = JSON.parse(host.args);
            return v && typeof v === "object" && !Array.isArray(v) ? v : {};
        } catch (_) { return {}; }
    }

    // 按类型校验录入原始值，返回错误文案；合法或空值返回空串（空值由必填校验负责）
    function argTypeError(p, raw) {
        if (raw === "" || raw == null) return "";
        if (p.type === "number") {
            return Number.isFinite(Number(raw)) ? "" : "必须为数字";
        }
        if (p.type === "boolean") {
            return raw === "true" || raw === "false" ? "" : "必须为是或否";
        }
        if (p.type === "enum") {
            const opts = (Array.isArray(p.options) ? p.options : []).map((o) => String(o));
            return opts.includes(String(raw)) ? "" : "必须为枚举项之一";
        }
        return "";
    }

    // 按类型把录入的原始字符串转换为 $args 中的规范值。
    function coerceArgValue(type, raw) {
        if (type === "boolean") return raw === "true";
        if (type === "number") { const n = Number(raw); return Number.isFinite(n) ? n : raw; }
        return raw;
    }

    function argValueFilled(value) {
        return value !== undefined && value !== null && String(value) !== "";
    }

    function paramValue(p, current) {
        return argValueFilled(current[p.name]) ? current[p.name] : p.default;
    }

    function paramControlHtml(p, current) {
        const name = escapeText(p.name);
        const value = paramValue(p, current);
        const val = argValueFilled(value) ? String(value) : "";
        if (p.type === "boolean") {
            return `<select class="field-control arg-control" data-arg="${name}" data-arg-type="boolean">
                <option value="">未设置</option>
                <option value="true" ${val === "true" ? "selected" : ""}>是</option>
                <option value="false" ${val === "false" ? "selected" : ""}>否</option>
            </select>`;
        }
        if (p.type === "enum") {
            const opts = Array.isArray(p.options) ? p.options : [];
            return `<select class="field-control arg-control" data-arg="${name}" data-arg-type="enum">
                <option value="">未设置</option>
                ${opts.map((o) => `<option value="${escapeText(o)}" ${String(o) === val ? "selected" : ""}>${escapeText(o)}</option>`).join("")}
            </select>`;
        }
        // 当前值非法时退化为文本框，否则原生 type=number 会丢弃该值、让脏数据在界面上凭空消失
        const numeric = p.type === "number" && !argTypeError(p, val)
            ? ' type="number" inputmode="decimal" step="any"' : "";
        const placeholder = p.type === "number" ? "请输入数字，如 18" : "请输入文本";
        return `<input class="field-control arg-control" data-arg="${name}" data-arg-type="${escapeText(p.type)}"${numeric}
            value="${escapeText(val)}" placeholder="${escapeText(placeholder)}" autocomplete="off">`;
    }

    // 参数类型中文名，与规则页参数模板的类型下拉保持一致
    const ARG_TYPE_LABELS = { string: "文本", number: "数字", boolean: "布尔", enum: "枚举" };

    // 渲染参数录入区 HTML（无参返回空串）。
    function renderParamsSectionHtml(node) {
        const defs = nodeParamDefs(node);
        if (!defs.length) return "";
        const current = parseArgs(argsHost(node));
        const rows = defs.map((p) => {
            const label = escapeText(p.label || p.name);
            const value = paramValue(p, current);
            const error = argTypeError(p, argValueFilled(value) ? String(value) : "");
            const state = error ? " arg-invalid" : (!argValueFilled(value) ? " arg-missing" : "");
            const hint = p.description ? `<span class="arg-hint">${escapeText(p.description)}</span>` : "";
            const optionCount = p.type === "enum" && Array.isArray(p.options) ? p.options.length : 0;
            const typeText = (ARG_TYPE_LABELS[p.type] || p.type)
                + (optionCount ? ` ${optionCount} 项` : "");
            return `<div class="detail-field arg-field${state}" data-arg-name="${escapeText(p.name)}">
                <span class="detail-label">${label} <span class="arg-req">*</span><span class="arg-type">${escapeText(typeText)}</span></span>
                ${paramControlHtml(p, current)}
                ${hint}
                <span class="arg-error" ${error ? "" : "hidden"}>${escapeText(error)}</span>
            </div>`;
        }).join("");
        return `<div class="args-section" data-args-section><div class="args-title">规则参数</div>${rows}</div>`;
    }

    // 从录入区收集 args 写回宿主节点（omit 空值），返回是否变更。container 为参数区所在容器。
    function commitArgsFromInputs(node, container) {
        const host = argsHost(node);
        const section = container && container.querySelector("[data-args-section]");
        if (!host || !section) return false;
        const values = {};
        nodeParamDefs(node).forEach((p) => {
            const control = section.querySelector(`[data-arg="${CSS.escape(p.name)}"]`);
            if (!control) return;
            const raw = control.value;
            if (raw === "" || raw == null) return; // 未填写：不写入该键
            if (argTypeError(p, raw)) return;      // 类型不符：不写入，保持 args 与声明类型一致
            values[p.name] = coerceArgValue(p.type, raw);
        });
        const next = Object.keys(values).length ? JSON.stringify(values) : "";
        if ((host.args || "") === next) return false;
        host.args = next;
        return true;
    }

    // 刷新参数录入区各行的必填缺失与类型错误标记（不重渲染，避免打断输入焦点）。
    function refreshArgMarks(node, container) {
        const section = container && container.querySelector("[data-args-section]");
        if (!section) return;
        const current = parseArgs(argsHost(node));
        nodeParamDefs(node).forEach((p) => {
            const row = section.querySelector(`[data-arg-name="${CSS.escape(p.name)}"]`);
            if (!row) return;
            const control = row.querySelector(`[data-arg="${CSS.escape(p.name)}"]`);
            const error = control ? argTypeError(p, control.value) : "";
            row.classList.toggle("arg-invalid", Boolean(error));
            row.classList.toggle("arg-missing", !error && !argValueFilled(paramValue(p, current)));
            const errorEl = row.querySelector(".arg-error");
            if (errorEl) {
                errorEl.textContent = error;
                errorEl.hidden = !error;
            }
        });
    }

    // 收集规则子树内参数未填写或类型不符的原子规则节点（含问题参数名），供保存前校验。
    function missingRequiredInRule(ruleRoot) {
        const missing = [];
        if (!ruleRoot) return missing;
        walk(ruleRoot, (node) => {
            if (T.hasArgs(node)) {
                const defs = nodeParamDefs(node);
                const current = parseArgs(node);
                const problems = [];
                defs.forEach((p) => {
                    const value = paramValue(p, current);
                    if (!argValueFilled(value)) { problems.push(p.label || p.name); return; }
                    const error = argTypeError(p, String(value));
                    if (error) problems.push(`${p.label || p.name}（${error}）`);
                });
                if (problems.length) missing.push({ node, params: problems });
            }
            return true;
        });
        return missing;
    }

    function materializeParamDefaults(ruleRoot) {
        walk(ruleRoot, (node) => {
            if (!T.hasArgs(node)) return true;
            const current = parseArgs(node);
            let changed = false;
            nodeParamDefs(node).forEach((p) => {
                if (argValueFilled(current[p.name]) || !argValueFilled(p.default)) return;
                if (argTypeError(p, String(p.default))) return; // 默认值与声明类型不符：不注入
                current[p.name] = coerceArgValue(p.type, String(p.default));
                changed = true;
            });
            if (changed) node.args = JSON.stringify(current);
            return true;
        });
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
        const pencil = (field) => editorReadOnly ? "" : `<button class="field-edit" type="button" data-field-edit="${field}" aria-label="编辑" title="编辑">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20h4L18.5 9.5a2 2 0 0 0-2.83-2.83L5 17v3z"/><path d="M13.5 6.5l4 4"/></svg>
        </button>`;
        const rulePencil = (label) => editorReadOnly ? "" : `<button class="field-edit" type="button" data-rule-config
                aria-label="编辑${label}" title="编辑${label}">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20h4L18.5 9.5a2 2 0 0 0-2.83-2.83L5 17v3z"/><path d="M13.5 6.5l4 4"/></svg>
        </button>`;
        const confirmCancel = () => `<div class="field-edit-actions">
            <button class="field-confirm" type="button" data-field-confirm aria-label="确认" title="确认"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 13l4 4L19 7"/></svg></button>
            <button class="field-cancel" type="button" data-field-cancel aria-label="取消" title="取消"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg></button>
        </div>`;
        if (inspectorEditableType(node.type)) {
            const nameActive = inspectorEditingField === editKey(nodeId, "name");
            parts.push(`<div class="detail-field editable${nameActive ? " field-editing" : ""}" data-field="name">
                <span class="detail-label">节点名称</span>
                <div class="field-row">
                    <span class="detail-value">${escapeText(nodeName || "未设置")}</span>
                    ${nameActive ? "" : pencil("name")}
                    <input class="field-control" id="inspectorNameInput" maxlength="28" autocomplete="off"
                           value="${escapeText(nodeName)}" placeholder="为该节点设置名称">
                    ${nameActive ? confirmCancel() : ""}
                </div>
            </div>`);
        }
        const typeOptions = D.conversionTypes(node);
        const typeEditable = typeOptions.length > 1;
        const typeActive = typeEditable && inspectorEditingField === editKey(nodeId, "type");
        if (!typeOptions.length) typeOptions.push(node.type);
        parts.push(`<div class="detail-field${typeEditable ? " editable" : ""}${typeActive ? " field-editing" : ""}" data-field="type">
            <span class="detail-label">节点类型</span>
            <div class="field-row">
                <span class="detail-value">${escapeText(TYPES[node.type].name)}</span>
                ${typeEditable && !typeActive ? pencil("type") : ""}
                <select class="field-control" id="inspectorTypeSelect">
                    ${typeOptions.map((type) => `<option value="${type}" ${node.type === type ? "selected" : ""}>${escapeText(TYPES[type].name)}</option>`).join("")}
                </select>
                ${typeActive ? confirmCancel() : ""}
            </div>
        </div>`);
        if (node.type === "ActionNode") {
            const selectedRuleId = node.rule?.expr;
            const ruleReference = selectedRuleId === "∅"
                ? "空动作"
                : definitionReference(ACTION_DEFINITIONS, selectedRuleId, flowNodeName(node));
            parts.push(`<div class="detail-field">
                <span class="detail-label">引用规则</span>
                <div class="field-row">
                    <span class="detail-value">${escapeText(ruleReference)}</span>
                    ${rulePencil("引用规则")}
                </div>
            </div>`);
        } else if (node.type === "ConditionNode") {
            parts.push(`<div class="detail-field">
                <span class="detail-label">判断条件</span>
                <div class="field-row">
                    <span class="detail-value rule-reference-value">${judgeRuleReferenceMarkup(node)}</span>
                    ${rulePencil("判断条件")}
                </div>
            </div>`);
        } else if (D.conversionTypes(node).length > 1) {
            parts.push(`<div class="detail-field">
                <span class="detail-label">分支数</span>
                <span class="detail-value">${node.branches.length} 个</span>
            </div>`);
        } else if (node.type === "DecisionNode") {
            parts.push(`<div class="detail-field">
                <span class="detail-label">分支数</span>
                <span class="detail-value">${node.branches.length + (node.defaultBranch ? 1 : 0)} 个</span>
            </div>`);
        } else if (node.type === "LoopNode") {
            parts.push(`<div class="detail-field">
                <span class="detail-label">最大循环次数</span>
                <span class="detail-value">${Number.isInteger(node.max) && node.max >= 0 ? node.max : "未设置"}</span>
            </div>`);
            parts.push(`<div class="detail-field">
                <span class="detail-label">循环条件</span>
                <div class="field-row">
                    <span class="detail-value rule-reference-value">${judgeRuleReferenceMarkup(node)}</span>
                    ${rulePencil("循环条件")}
                </div>
            </div>`);
        }
        $("#nodeDetailFields").innerHTML = parts.join("");
        if (inspectorEditingField && inspectorEditingField.startsWith(`${nodeId}:`)) {
            requestAnimationFrame(() => {
                const control = $("#nodeDetailFields .field-editing .rule-picker-filter, "
                    + "#nodeDetailFields .field-editing input.field-control, "
                    + "#nodeDetailFields .field-editing select.field-control");
                control?.focus();
                if (control?.tagName === "INPUT") control.select();
            });
        }
    }

    function updateInspector() {
        const found = selectedId ? findNode(selectedId) : null;
        const empty = $("#inspectorEmpty");
        const form = $("#inspectorForm");
        const testPanel = $("#testPanel");
        if (testPanelOpen) {
            testPanel.hidden = false;
            empty.hidden = true;
            form.hidden = true;
            return;
        }
        testPanel.hidden = true;
        if (!found) { empty.hidden = false; form.hidden = true; return; }
        const { node } = found;
        empty.hidden = true;
        form.hidden = false;
        renderInspectorHeading();
        renderInspectorFields(node);
        $("#inspectorAddPanel").hidden = true;
        const operations = flowNodeOperations(node);
        // 主操作（添加后续/分支）在前、强调；次操作「前插节点」在后、弱化并分隔，避免误点。
        const addButtons = operations.map((operation) => `
            <button class="flow-operation wide add-op" type="button" data-flow-relation="${operation.relation}">
                ${escapeText(operation.label)}
            </button>`).join("");
        const insertButton = node.type !== "TreeNode"
            ? `<button class="flow-operation wide insert-op" type="button" data-insert-before title="在当前节点之前插入一个新节点（会包裹当前节点）">向前插入</button>`
            : "";
        $("#flowNodeActions").innerHTML = addButtons + insertButton;
        const editButton = $("#editNodeButton");
        const locallyConfigurable = D.isLocallyConfigurable(node) && editorReadOnly;
        editButton.hidden = !locallyConfigurable;
        editButton.textContent = editorReadOnly
            ? "查看配置"
            : (isLocallyUnconfigured(node) ? "配置此节点" : "编辑配置");
        // 插入节点按钮对所有非根节点都存在，故只要不是根节点该操作区就应可见，
        // 否则「有后继、非判断」的节点（如根下第一个动作节点）会误藏插入入口。
        const hasInsert = node.type !== "TreeNode";
        $("#inspectorViewActions").hidden = !hasInsert && operations.length === 0 && editButton.hidden;
        $("#inspectorFooter").hidden = node.type === "TreeNode";
        $("#deleteButton").disabled = !canDeleteFlowNode(node, found.parent, found.relation);
        $("#deleteButton").title = "";
    }

    function beginFieldEdit(field) {
        if (editorReadOnly) return;
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !inspectorEditableType(found.node.type)) return;
        inspectorEditingField = editKey(idOf(found.node), field);
        renderInspectorHeading();
        renderInspectorFields(found.node);
    }

    function confirmFieldEdit() {
        if (editorReadOnly) return;
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !inspectorEditingField) return;
        const node = found.node;
        const before = JSON.stringify(node);
        const field = inspectorEditingField.split(":")[1];
        if (field === "name") {
            setFlowNodeName(node, $("#inspectorNameInput").value.trim());
        } else if (field === "type" && D.conversionTypes(node).length > 1) {
            const newType = $("#inspectorTypeSelect").value;
            if (D.conversionTypes(node).includes(newType)) node.type = newType;
        }
        inspectorEditingField = null;
        render({ preserveView: true });
        if (before !== JSON.stringify(node)) markDraft();
    }

    // 所有节点名称统一写入 NameNode.name
    function setFlowNodeName(node, value) {
        node.name = value;
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

    function openLocalNodeConfig(nodeId) {
        const node = findNode(nodeId)?.node;
        if (!node) return;
        if (node.type === "ActionNode") openActionConfig(nodeId);
        else if (isConditionOwner(node)) openRuleDialog(nodeId);
    }

    function confirmDiscardInspectorEdit() { inspectorEditingField = null; return true; }

    // ---- 规则弹窗（在条件拥有者的规则子树上操作）----
    function currentRuleJudge() {
        if (ruleJudgeDraft) return ruleJudgeDraft;
        const found = ruleJudgeId ? findNode(ruleJudgeId) : null;
        return isConditionOwner(found?.node) ? found.node : null;
    }

    // 在当前条件规则子树中定位规则节点，根规则的 parent 是条件拥有者
    function findRuleNode(id) {
        const judge = currentRuleJudge();
        const root = judge ? conditionRule(judge) : null;
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
        const type = TYPES[node.type] || TYPES.BooleanNode;
        const reorderable = Boolean(parent && D.isCompositeRule(parent) && (parent.rules?.length || 0) >= 2);
        const ruleExpression = node.type === "LogicNode" ? node.expr : (node.type === "BooleanNode" ? node.expr : "");
        const expression = ruleExpression ? `<span class="node-expression">${escapeText(ruleExpression)}</span>` : "";
        const kids = childNodes(node);
        const children = kids.length
            ? `<div class="rule-children">${kids.map((child) => renderRuleBranch(child, node)).join("")}</div>` : "";
        const title = ruleNodeCanvasLabel(node);
        const cardTitle = `${node.negative ? "取反 · " : ""}${type.kind === "structure" ? type.name : title}`;
        const negateBadge = node.negative ? `<span class="negate-badge" title="取反">非</span>` : "";
        const id = idOf(node);
        return `
            <div class="rule-branch" data-rule-branch-id="${id}">
                <div class="node-shell"${reorderable ? ' data-reorderable="1"' : ""} data-rule-node-id="${id}" tabindex="0" role="treeitem" aria-selected="${ruleSelectedId === id}">
                    <div class="node-card ${type.kind} node-type-${node.type.toLowerCase()}${node.negative ? " is-negated" : ""}" title="${escapeText(cardTitle)}" aria-label="${escapeText(cardTitle)}">
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
        if (isConditionOwner(found.parent)) return false;
        if (found.parent?.type === "MatchNode" && (found.parent.rules?.length || 0) <= 1) return false;
        return true;
    }

    function renderRuleTree() {
        const judge = currentRuleJudge();
        const rule = judge ? conditionRule(judge) : null;
        if (!judge) return;
        if (ruleSelectedId && !findRuleNode(ruleSelectedId)) ruleSelectedId = rule ? idOf(rule) : null;
        ruleTreeRoot.innerHTML = rule
            ? renderRuleBranch(rule)
            : `<div class="rule-tree-empty">
                <span>暂无条件</span>
                <button class="primary" id="ruleAddRootButton" type="button">＋ 配置根规则</button>
            </div>`;
    }

    function ruleNodeSubrules(node) {
        if (node.type === "BooleanNode") return [node];
        if (D.isCompositeRule(node)) return node.rules.slice();
        return [];
    }

    function subruleRowLabel(child) {
        if (child.type === "BooleanNode") {
            const definition = ruleDefinitionById(child.expr);
            return definition ? `${definition.desc} · ${definition.ruleId}` : (child.expr || "未设置");
        }
        return ruleNodeDisplayName(child);
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
        const canReorder = D.isCompositeRule(node);
        container.innerHTML = refs.map((child, index) => {
            const lastIndex = refs.length - 1;
            const activeClass = idOf(child) === highlightedRuleRowId ? " rule-row-active" : "";
            const moveButtons = canReorder ? `
                <button type="button" data-subrule-up="${index}" aria-label="上移" title="上移" ${index === 0 ? "disabled" : ""}>↑</button>
                <button type="button" data-subrule-down="${index}" aria-label="下移" title="下移" ${index === lastIndex ? "disabled" : ""}>↓</button>
            ` : "";
            if (child.type !== "BooleanNode") {
                return `<div class="rule-add-row rule-subrule-static${activeClass}">
                    <span class="detail-value">${escapeText(subruleRowLabel(child))}</span>
                    ${moveButtons}
                </div>`;
            }
            return `<div class="rule-add-row${activeClass}">
                ${definitionPickerHtml(
                    `ruleSubrule${index}`,
                    "rule",
                    RULE_DEFINITIONS,
                    child.expr,
                    child.expr,
                    `data-subrule-select="${index}"`
                )}
                ${moveButtons}
            </div>`;
        }).join("");
        $("#ruleEditorAddRuleButton").hidden = !D.canAddRule(node);
        renderRuleLogicField();
    }

    let ruleLogicEditing = false;
    let highlightedRuleRowId = null;

    function renderRuleLogicField() {
        const container = $("#ruleEditorLogicField");
        if (!container) return;
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node) { container.hidden = true; return; }
        container.hidden = false;
        if (node.type === "BooleanNode") {
            ruleLogicEditing = false;
            container.className = "detail-field";
            container.innerHTML = `<span class="detail-label">节点类型</span>
                <div class="field-row"><span class="detail-value">原子节点</span></div>`;
            return;
        }
        if (!D.isCompositeRule(node)) { container.hidden = true; return; }
        const operator = D.ruleOperator(node);
        const value = operator.value;
        const label = operator.label;
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
                <select class="field-control" id="ruleLogicSelect">${D.RULE_OPERATORS.map((item) =>
                    `<option value="${item.value}" ${value === item.value ? "selected" : ""}>${item.label}</option>`
                ).join("")}</select>
                ${ruleLogicEditing ? confirmCancel : ""}
            </div>
            ${ruleLogicEditing ? `<div class="rule-match-bounds" id="ruleMatchBounds" ${value === "some" ? "" : "hidden"}>
                <label>
                    <span class="detail-label">最少匹配</span>
                    <input class="field-control" id="ruleMatchMin" type="number" min="0" max="${node.rules.length}" step="1"
                           value="${node.type === "MatchNode" && node.minHits != null ? node.minHits : ""}" placeholder="不限">
                </label>
                <label>
                    <span class="detail-label">最多匹配</span>
                    <input class="field-control" id="ruleMatchMax" type="number" min="0" max="${node.rules.length}" step="1"
                           value="${node.type === "MatchNode" && node.maxHits != null ? node.maxHits : ""}" placeholder="不限">
                </label>
            </div>` : ""}`;
    }

    function beginRuleLogicEdit() {
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node || !D.isCompositeRule(node)) return;
        ruleLogicEditing = true;
        renderRuleLogicField();
    }

    function confirmRuleLogicEdit() {
        const found = findRuleNode(ruleSelectedId);
        if (!found || !D.isCompositeRule(found.node)) {
            ruleLogicEditing = false;
            renderRuleLogicField();
            return;
        }
        const node = found.node;
        const value = $("#ruleLogicSelect")?.value || "&&";
        if (value === "some") {
            const bounds = parseMatchBounds($("#ruleMatchMin").value, $("#ruleMatchMax").value, node.rules.length);
            if (bounds.error) {
                openConfirm(bounds.error, { title: "节点尚未完成", confirmLabel: "知道了" });
                return;
            }
            node.type = "MatchNode";
            node.expr = "some";
            node.minHits = bounds.minHits;
            node.maxHits = bounds.maxHits;
        } else {
            if (node.rules.length < 2) {
                openConfirm("条件与和条件或至少需要两个子规则", { title: "无法转换", confirmLabel: "知道了" });
                return;
            }
            node.type = "LogicNode";
            node.expr = value === "||" ? "||" : "&&";
            delete node.minHits;
            delete node.maxHits;
        }
        ruleLogicEditing = false;
        renderRuleTree();
        updateRuleEditor();
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
            const hasRule = Boolean(currentRuleJudge() && conditionRule(currentRuleJudge()));
            $("#ruleEditorEmptyTitle").textContent = hasRule ? "未选择规则" : "暂无条件";
            empty.hidden = false;
            panel.hidden = true;
            return;
        }
        const { node } = found;
        empty.hidden = true;
        panel.hidden = false;
        renderRuleEditorHeading();
        const isHits = node.type === "MatchNode";
        $("#ruleReferenceField").hidden = !isHits;
        $("#ruleReferenceLabel").textContent = "匹配数量";
        $("#ruleNodeExpression").value = isHits ? matchRequirementLabel(node) : "";
        $("#ruleNodeExpression").readOnly = true;
        $("#ruleEditorSubrulesField").hidden = false;
        renderRuleEditorSubrules();
        renderRuleEditorParams(node);
        $("#ruleNegateField").hidden = false;
        $("#ruleNegateInput").checked = !!node.negative;
        $("#ruleAddPanel").hidden = true;
        $("#ruleEditorFooter").hidden = false;
        const deletable = canDeleteRule(found);
        $("#ruleEditorDeleteButton").disabled = !deletable;
        $("#ruleEditorDeleteButton").title = deletable
            ? ""
            : (isConditionOwner(found.parent)
                ? "条件必须保留根规则，不能删除"
                : "组合规则至少需要保留两个子规则，不能直接删除");
    }

    // 规则弹窗内选中原子规则节点且其引用规则声明了模板参数时渲染参数录入区并绑定到该 BooleanNode 的 args
    function renderRuleEditorParams(node) {
        const field = $("#ruleEditorParamsField");
        if (!field) return;
        if (!node || node.type !== "BooleanNode") { field.hidden = true; field.innerHTML = ""; return; }
        const html = renderParamsSectionHtml(node);
        field.innerHTML = html;
        field.hidden = !html;
    }

    function currentEditingNode() { return findRuleNode(ruleSelectedId)?.node || null; }

    // 用幸存子规则替换 LogicNode，若其是 ConditionNode 的根规则则替换 rule，否则替换父组合节点 rules 中的该项
    function collapseLogicToSurvivor(logicNode, survivor) {
        const found = findRuleNode(idOf(logicNode));
        const parent = found ? found.parent : currentRuleJudge();
        if (!parent) return;
        if (isConditionOwner(parent)) {
            setConditionRule(parent, survivor);
        } else if (D.isCompositeRule(parent)) {
            const idx = parent.rules.indexOf(logicNode);
            if (idx >= 0) parent.rules.splice(idx, 1, survivor);
        }
        ruleSelectedId = idOf(survivor);
    }

    function removeRuleEditorSubrule(index) {
        const node = currentEditingNode();
        if (!node || !D.isCompositeRule(node)) return;
        if (index < 0 || index >= node.rules.length) return;
        if (node.type === "LogicNode" && node.rules.length <= 2) {
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
        if (!node || !D.isCompositeRule(node)) return;
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

    const RULE_BUILDER_VIEWS = {
        root: { rows: "#ruleAddRows", logic: "#ruleAddLogicField", prefix: "ruleAdd" },
        dialog: { rows: "#ruleCompositeRows", logic: "#ruleCompositeLogicField", prefix: "ruleComposite" }
    };
    let ruleBuilderState = null;

    function ruleBuilderView() {
        return ruleBuilderState?.mode === "root" ? RULE_BUILDER_VIEWS.root : RULE_BUILDER_VIEWS.dialog;
    }

    function ruleBuilderRuleCount() {
        return (ruleBuilderState?.rows.length || 0) + (ruleBuilderState?.promoteFrom ? 1 : 0);
    }

    function stagingRuleNode(row) {
        return { type: "BooleanNode", expr: row?.ruleId || "", args: row?.args || "" };
    }

    function renderRuleBuilder(focusIndex = -1) {
        if (!ruleBuilderState) return;
        const view = ruleBuilderView();
        const rows = $(view.rows);
        rows.innerHTML = ruleBuilderState.rows.map((row, index) => {
            const params = renderParamsSectionHtml(stagingRuleNode(row));
            return `<div class="rule-add-row">
                <div class="rule-add-field">
                    <span class="detail-label">引用规则 ${index + 1}</span>
                    ${definitionPickerHtml(
                        `${view.prefix}Definition${index}`,
                        "rule",
                        RULE_DEFINITIONS,
                        row.ruleId,
                        row.ruleId,
                        `data-rule-builder-select="${index}"`
                    )}
                </div>
                <button type="button" data-rule-builder-remove="${index}" aria-label="删除规则 ${index + 1}"
                        ${ruleBuilderState.rows.length === 1 ? "disabled" : ""}>×</button>
            </div>
            ${params ? `<div class="rule-add-params" data-rule-builder-params="${index}">${params}</div>` : ""}`;
        }).join("");
        renderRuleBuilderLogic();
        requestAnimationFrame(() => {
            if (focusIndex < 0) return;
            const valueInput = rows.querySelector(`[data-rule-builder-select="${focusIndex}"]`);
            valueInput?.closest(".rule-picker")?.querySelector(".rule-picker-filter")?.focus();
        });
    }

    function renderRuleBuilderLogic() {
        if (!ruleBuilderState) return;
        const field = $(ruleBuilderView().logic);
        const count = ruleBuilderRuleCount();
        field.hidden = count < 2;
        if (field.hidden) {
            field.innerHTML = "";
            return;
        }
        field.innerHTML = `<legend>组合方式</legend>
            <div class="rule-radio-group">${D.RULE_OPERATORS.map((operator) => `<label>
                <input type="radio" name="activeRuleBuilderLogic" value="${operator.value}"
                       ${ruleBuilderState.operator === operator.value ? "checked" : ""}>
                <span>${operator.label}</span>
            </label>`).join("")}</div>
            <div class="rule-match-bounds rule-builder-bounds" ${ruleBuilderState.operator === "some" ? "" : "hidden"}>
                <label><span>最少匹配</span><input data-rule-builder-min type="number" min="0" max="${count}"
                    step="1" placeholder="不限" value="${escapeText(ruleBuilderState.minHits)}"></label>
                <label><span>最多匹配</span><input data-rule-builder-max type="number" min="0" max="${count}"
                    step="1" placeholder="不限" value="${escapeText(ruleBuilderState.maxHits)}"></label>
            </div>`;
    }

    function openRuleBuilder(mode, promoteFrom = null) {
        const firstId = RULE_DEFINITIONS.find((definition) => definition.ruleId !== promoteFrom?.expr)?.ruleId
            || RULE_DEFINITIONS[0]?.ruleId || "";
        ruleBuilderState = {
            mode,
            promoteFrom,
            operator: "&&",
            minHits: "",
            maxHits: "",
            rows: [{ ruleId: firstId, args: "" }]
        };
        renderRuleBuilder(0);
    }

    function appendRuleBuilderRow() {
        if (!ruleBuilderState) return;
        const used = new Set(ruleBuilderState.rows.map((row) => row.ruleId));
        if (ruleBuilderState.promoteFrom?.expr) used.add(ruleBuilderState.promoteFrom.expr);
        const unused = RULE_DEFINITIONS.find((definition) => !used.has(definition.ruleId));
        ruleBuilderState.rows.push({ ruleId: unused?.ruleId || RULE_DEFINITIONS[0]?.ruleId || "", args: "" });
        renderRuleBuilder(ruleBuilderState.rows.length - 1);
    }

    function removeRuleBuilderRow(index) {
        if (!ruleBuilderState || ruleBuilderState.rows.length <= 1
            || index < 0 || index >= ruleBuilderState.rows.length) return;
        ruleBuilderState.rows.splice(index, 1);
        renderRuleBuilder(Math.min(index, ruleBuilderState.rows.length - 1));
    }

    function cancelRuleBuilder() {
        const root = ruleBuilderState?.mode === "root";
        ruleBuilderState = null;
        if (root) {
            $("#ruleAddPanel").hidden = true;
            updateRuleEditor();
        } else if ($("#ruleAddDialog").open) {
            $("#ruleAddDialog").close();
        }
    }

    function openCompositeBuilder() {
        const node = findRuleNode(ruleSelectedId)?.node;
        if (!node || !D.canAddRule(node)) return;
        openRuleBuilder("dialog", node.type === "BooleanNode" ? node : null);
        $("#ruleAddDialog").showModal();
    }

    function renderRuleDialog() {
        const judge = currentRuleJudge();
        if (!judge) return;
        const loop = judge.type === "LoopNode";
        const adding = Boolean(ruleJudgeDraft);
        $("#ruleDialogTitle").textContent = loop
            ? (adding ? "添加循环" : "配置循环")
            : (adding ? "添加条件" : "配置条件");
        $("#ruleDialogLoopMaxField").hidden = !loop;
        if (loop) $("#ruleDialogLoopMax").value = ruleDialogLoopMaxDraft;
        renderRuleTree();
        updateRuleEditor();
        syncReadonlyControls();
    }

    let ruleDialogSnapshot = null;
    let ruleDialogCommitted = false;
    let ruleDialogLoopMaxDraft = "";

    function cloneNode(node) { return node ? T.deserialize(T.serialize(node)) : null; }

    function openRuleDialog(judgeId) {
        const found = findNode(judgeId);
        if (!found || !isConditionOwner(found.node)) return;
        ruleJudgeDraft = null;
        ruleDraftCommit = null;
        ruleJudgeId = judgeId;
        ruleDialogLoopMaxDraft = found.node.type === "LoopNode" && Number.isInteger(found.node.max) && found.node.max >= 0
            ? String(found.node.max) : "";
        ruleDialogSnapshot = {
            rule: cloneNode(conditionRule(found.node)),
            max: found.node.type === "LoopNode" ? found.node.max : null
        };
        ruleDialogCommitted = false;
        const rule = conditionRule(found.node);
        ruleSelectedId = rule ? idOf(rule) : null;
        hideRulePopover();
        renderRuleDialog();
        if (!ruleDialog.open) ruleDialog.showModal();
    }

    function openRuleDraft(node, onCommit) {
        ruleJudgeId = null;
        ruleJudgeDraft = node;
        ruleDraftCommit = onCommit;
        ruleDialogLoopMaxDraft = node.type === "LoopNode" && Number.isInteger(node.max) && node.max >= 0
            ? String(node.max) : "";
        ruleDialogSnapshot = null;
        ruleDialogCommitted = false;
        const rule = conditionRule(node);
        ruleSelectedId = rule ? idOf(rule) : null;
        hideRulePopover();
        renderRuleDialog();
        if (!ruleDialog.open) ruleDialog.showModal();
    }

    function hideRulePopover() {
        $("#ruleAddPanel").hidden = true;
        if (ruleBuilderState?.mode === "root") ruleBuilderState = null;
    }

    function ruleDefinitionById(ruleId) { return RULE_DEFINITION_BY_ID.get(ruleId) || null; }
    function actionDefinitionById(ruleId) { return ACTION_DEFINITION_BY_ID.get(ruleId) || null; }

    function createAtomicRule(ruleId, name = "") {
        const definition = ruleDefinitionById(ruleId);
        if (!definition) return null;
        return make.atom(definition.ruleId, name.trim());
    }

    function createCompositeRule(operator, name, rules, bounds = {}) {
        const node = operator === "some"
            ? make.some(bounds.minHits, bounds.maxHits)
            : make.logic(operator);
        node.name = name.trim();
        rules.forEach((rule) => node.rules.push(rule));
        return node;
    }

    function parseMatchBounds(minValue, maxValue, ruleCount) {
        const minText = String(minValue ?? "").trim();
        const maxText = String(maxValue ?? "").trim();
        if (minText === "" && maxText === "") {
            return { error: "最少匹配和最多匹配不能同时为空" };
        }
        const minHits = minText === "" ? null : Number(minText);
        const maxHits = maxText === "" ? null : Number(maxText);
        const min = minHits == null ? 0 : minHits;
        const max = maxHits == null ? ruleCount : maxHits;
        if (!Number.isInteger(min) || !Number.isInteger(max) || min < 0 || min > max || max > ruleCount) {
            return { error: `匹配数量范围必须满足 0 ≤ 最少匹配 ≤ 最多匹配 ≤ 子规则数（${ruleCount}）` };
        }
        return { minHits, maxHits };
    }

    function ruleBuilderChoice() {
        if (!ruleBuilderState) return null;
        const operator = ruleBuilderRuleCount() < 2 ? "&&" : ruleBuilderState.operator;
        if (operator !== "some") return { operator, bounds: {} };
        const bounds = parseMatchBounds(ruleBuilderState.minHits, ruleBuilderState.maxHits, ruleBuilderRuleCount());
        if (bounds.error) {
            openConfirm(bounds.error, { title: "节点尚未完成", confirmLabel: "知道了" });
            return null;
        }
        return { operator, bounds };
    }

    function startRuleAdd() {
        const judge = currentRuleJudge();
        if (!judge || conditionRule(judge)) return;
        $("#ruleEditorEmpty").hidden = true;
        $("#ruleEditorPanel").hidden = false;
        $("#ruleEditorHeading").innerHTML = "";
        $("#ruleEditorSubrulesField").hidden = true;
        $("#ruleEditorLogicField").hidden = true;
        $("#ruleNegateField").hidden = true;
        $("#ruleReferenceField").hidden = true;
        $("#ruleAddTitle").textContent = "配置根规则";
        $("#ruleEditorFooter").hidden = true;
        $("#ruleAddPanel").hidden = false;
        openRuleBuilder("root");
    }

    function createRuleBuilderRules() {
        if (!ruleBuilderState) return null;
        const rules = ruleBuilderState.rows.map((row) => {
            const rule = createAtomicRule(row.ruleId);
            if (rule && row.args) rule.args = row.args;
            return rule;
        });
        return rules.some((rule) => !rule) ? null : rules;
    }

    function createRuleBuilderNode(rules, choice) {
        return rules.length === 1 ? rules[0] : createCompositeRule(choice.operator, "", rules, choice.bounds);
    }

    function closeRuleBuilderDialog() {
        if ($("#ruleAddDialog").open) $("#ruleAddDialog").close();
        ruleBuilderState = null;
    }

    function confirmRuleBuilder() {
        if (!ruleBuilderState) return;
        const choice = ruleBuilderChoice();
        const rules = createRuleBuilderRules();
        if (!choice || !rules) return;

        if (ruleBuilderState.mode === "root") {
            const judge = currentRuleJudge();
            if (!judge || conditionRule(judge)) return;
            const node = createRuleBuilderNode(rules, choice);
            const blocked = limitBlockReason(judge, node);
            if (blocked) { openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" }); return; }
            setConditionRule(judge, node);
            ruleSelectedId = idOf(node);
            ruleBuilderState = null;
            hideRulePopover();
            renderRuleDialog();
            render({ preserveView: true });
            return;
        }

        const found = findRuleNode(ruleSelectedId);
        const node = found?.node;
        if (!node) return;

        if (ruleBuilderState.promoteFrom && node.type === "BooleanNode") {
            const composite = createCompositeRule(choice.operator, "", [cloneNode(node), ...rules], choice.bounds);
            const blocked = limitBlockReason(found.parent, composite, 1);
            if (blocked) {
                closeRuleBuilderDialog();
                openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" });
                return;
            }
            const parent = found.parent;
            if (isConditionOwner(parent)) {
                setConditionRule(parent, composite);
            } else if (D.isCompositeRule(parent)) {
                const index = parent.rules.indexOf(node);
                if (index >= 0) parent.rules.splice(index, 1, composite);
            }
            closeRuleBuilderDialog();
            ruleSelectedId = idOf(composite);
            renderRuleTree();
            updateRuleEditor();
            return;
        }

        if (!D.isCompositeRule(node)) return;
        const added = createRuleBuilderNode(rules, choice);
        const blocked = limitBlockReason(node, added);
        if (blocked) {
            closeRuleBuilderDialog();
            openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" });
            return;
        }
        node.rules.push(added);
        closeRuleBuilderDialog();
        renderRuleTree();
        updateRuleEditor();
    }

    async function deleteSelectedRule() {
        if (editorReadOnly) return;
        const found = findRuleNode(ruleSelectedId);
        if (!canDeleteRule(found)) return;
        const descendants = countNodes(found.node) - 1;
        const name = ruleNodeDisplayName(found.node);
        const message = descendants ? `删除「${name}」及其 ${descendants} 个子规则？` : `删除「${name}」？`;
        if (!(await openConfirm(message, { title: "确认删除", confirmLabel: "删除" }))) return;
        hideRulePopover();
        const parent = found.parent;
        if (!parent || !D.isCompositeRule(parent)) return;
        const idx = parent.rules.indexOf(found.node);
        if (idx >= 0) parent.rules.splice(idx, 1);
        if (parent.type === "LogicNode" && parent.rules.length === 1) {
            collapseLogicToSurvivor(parent, parent.rules[0]);
        } else {
            ruleSelectedId = D.isCompositeRule(parent) ? idOf(parent) : null;
        }
        renderRuleDialog();
        render({ preserveView: true });
    }

    function isLocallyUnconfigured(node) {
        return D.selfIssues(node).length > 0;
    }

    // 创建节点时只建立当前层语义结构
    function buildNodeScaffold(type) {
        if (type === "ActionNode") return make.action(ACTION_DEFINITIONS[0]?.ruleId || "");
        if (type === "ConditionNode") return make.condition(null);
        if (type === "SerialNode") return make.serial();
        if (type === "ParallelNode") return make.parallel();
        if (type === "DecisionNode") return make.decision();
        if (type === "LoopNode") return make.loop(-1, null, null);
        return null;
    }

    // 把新建节点挂到父节点的目标关系边上（列表边支持定位）
    function attachChild(parent, relation, node, placement, anchorId) {
        if (relation === "next") { parent.next = node; return; }
        if (relation === "default") { parent.defaultBranch = node; return; }
        const list = parent.branches;
        if (placement === "first") { list.unshift(node); return; }
        if (placement === "before" || placement === "after") {
            const anchorIndex = list.findIndex((child) => idOf(child) === anchorId);
            if (anchorIndex >= 0) { list.splice(placement === "before" ? anchorIndex : anchorIndex + 1, 0, node); return; }
        }
        list.push(node);
    }

    function openInsertPanel() {
        if (editorReadOnly) return;
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || found.node.type === "TreeNode") return;
        if (found.relation === "decision") {
            openConfirm("决策分支只能是条件节点，不能在其前插入其它结构。", { title: "无法插入", confirmLabel: "知道了" });
            return;
        }
        insertBeforeMode = true;
        $("#inspectorAddTitle").textContent = "向前插入（包裹为结构）";
        populateTypeSelect($("#newNodeType"), D.conversionTypes("SerialNode"));
        $("#newNodeType").value = "SerialNode";
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

    // 用真实节点替换目标（按其所在的关系边）
    function replaceChild(parent, oldNode, relation, real) {
        const edge = childEdges(parent).find((candidate) => candidate.node === oldNode && candidate.relation === relation);
        if (!edge) return;
        if (edge.kind === "list") edge.list.splice(edge.index, 1, real);
        else parent[edge.field] = real;
    }

    // 节点自身配置完整后才挂入规则树
    let configTarget = null;
    let actionConfigDraft = null;
    function openRuleConfigForNewNode(node, parentId, relation) {
        openRuleDraft(node, (configuredNode) => {
            const parent = findNode(parentId)?.node;
            if (!parent || !availableRelations(parent).includes(relation)) return;
            setCollapsed(parent, false);
            attachChild(parent, relation, configuredNode, "last", null);
            selectedId = idOf(configuredNode);
            fitMode = false;
        });
    }

    function startAddConfig(relation, parentId = selectedId) {
        if (editorReadOnly) return;
        const found = parentId ? findNode(parentId) : null;
        if (!found || !availableRelations(found.node).includes(relation)) return;
        if (relation === "decision") {
            const node = buildNodeScaffold("ConditionNode");
            const blocked = limitBlockReason(found.node, node);
            if (blocked) {
                openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" });
                return;
            }
            openRuleConfigForNewNode(node, idOf(found.node), relation);
            return;
        }
        configTarget = { mode: "addType", parentId: idOf(found.node), relation };
        openConfigDialog();
    }

    function updateNodeConfigStep() {
        const type = $("#nodeConfigType").value;
        if (type === "ActionNode") {
            if (!actionConfigDraft) actionConfigDraft = buildNodeScaffold(type);
            renderActionConfig();
            return;
        }
        actionConfigDraft = null;
        $("#nodeConfigTitle").textContent = "添加节点";
        $("#nodeConfigTypeField").hidden = false;
        $("#actionConfigFields").hidden = true;
        $("#nodeConfigCancelButton").hidden = false;
        $("#nodeConfigConfirmButton").textContent = "添加";
    }

    function openConfigDialog() {
        const dlg = document.getElementById("nodeConfigDialog");
        const relation = configTarget?.relation;
        $("#nodeConfigTitle").textContent = "添加节点";
        $("#nodeConfigTypeField").hidden = false;
        $("#actionConfigFields").hidden = true;
        $("#nodeConfigCancelButton").hidden = false;
        const parent = findNode(configTarget?.parentId)?.node;
        populateTypeSelect($("#nodeConfigType"), D.relationTypes(parent, relation));
        $("#nodeConfigType").value = $("#nodeConfigType").options[0]?.value || "ActionNode";
        updateNodeConfigStep();
        if (!dlg.open) dlg.showModal();
    }

    function openActionConfig(nodeId) {
        const node = findNode(nodeId)?.node;
        if (!node || node.type !== "ActionNode") return;
        configTarget = { mode: "editAction", nodeId };
        actionConfigDraft = make.action(node.rule?.expr || "");
        actionConfigDraft.rule = cloneNode(node.rule);
        renderActionConfig();
        const dlg = document.getElementById("nodeConfigDialog");
        if (!dlg.open) dlg.showModal();
        syncReadonlyControls();
    }

    function renderActionConfig() {
        if (!actionConfigDraft) return;
        const selectedRuleId = actionConfigDraft.rule?.expr || "";
        const inlineAdding = configTarget?.mode === "addType";
        const adding = inlineAdding || configTarget?.mode === "addAction";
        $("#nodeConfigTitle").textContent = inlineAdding ? "添加节点" : (adding ? "添加动作" : "配置动作");
        $("#nodeConfigTypeField").hidden = !inlineAdding;
        $("#actionConfigFields").hidden = false;
        $("#nodeConfigConfirmButton").textContent = editorReadOnly ? "关闭" : (adding ? "添加" : "保存");
        $("#nodeConfigCancelButton").hidden = editorReadOnly;
        $("#actionConfigRule").innerHTML = definitionPickerHtml(
            "actionConfigDefinition", "action", ACTION_DEFINITIONS, selectedRuleId);
        $("#actionConfigParams").innerHTML = renderParamsSectionHtml(actionConfigDraft);
        refreshArgMarks(actionConfigDraft, $("#actionConfigParams"));
    }

    function closeNodeConfig() {
        const dlg = document.getElementById("nodeConfigDialog");
        if (dlg.open) dlg.close();
        configTarget = null;
        actionConfigDraft = null;
    }

    function confirmNodeConfig() {
        if (editorReadOnly) { closeNodeConfig(); return; }
        if (!configTarget) { closeNodeConfig(); return; }
        if (["addAction", "editAction"].includes(configTarget.mode)) {
            confirmActionConfig();
            return;
        }
        const type = $("#nodeConfigType").value;
        const node = buildNodeScaffold(type);
        if (!node) return;
        const target = findNode(configTarget.parentId)?.node;
        if (!target) return;
        const blocked = limitBlockReason(target, node);
        if (blocked) { openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" }); return; }
        if (type === "ActionNode") {
            if (!actionConfigDraft) actionConfigDraft = node;
            confirmActionConfig();
            return;
        }
        if (D.isConditionOwner(type)) {
            const parentId = configTarget.parentId;
            const relation = configTarget.relation;
            closeNodeConfig();
            openRuleConfigForNewNode(node, parentId, relation);
            return;
        }
        setCollapsed(target, false);
        attachChild(target, configTarget.relation, node, "last", null);
        closeNodeConfig();
        selectedId = idOf(node);
        fitMode = false;
        render({ preserveView: true });
        markDraft();
    }

    function confirmActionConfig() {
        const selectedRuleId = $("#actionConfigDefinitionSelect")?.value || "";
        if (!actionConfigDraft || !selectedRuleId) {
            openConfirm("请选择动作规则", { title: "动作尚未完成", confirmLabel: "知道了" });
            return;
        }
        commitArgsFromInputs(actionConfigDraft, $("#actionConfigParams"));
        const missing = missingRequiredInRule(actionConfigDraft.rule);
        if (missing.length) {
            refreshArgMarks(actionConfigDraft, $("#actionConfigParams"));
            const names = [...new Set(missing.flatMap((item) => item.params))];
            openConfirm(`请先修正规则参数：${names.join("、")}`,
                { title: "参数不完整或类型不符", confirmLabel: "知道了" });
            return;
        }
        if (configTarget?.mode === "addAction"
            || (configTarget?.mode === "addType" && $("#nodeConfigType").value === "ActionNode")) {
            const target = findNode(configTarget.parentId)?.node;
            if (!target) return;
            const node = cloneNode(actionConfigDraft);
            const blocked = limitBlockReason(target, node);
            if (blocked) { openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" }); return; }
            const relation = configTarget.relation;
            setCollapsed(target, false);
            attachChild(target, relation, node, "last", null);
            const nodeId = idOf(node);
            closeNodeConfig();
            selectedId = nodeId;
            fitMode = false;
            render({ preserveView: true });
            markDraft();
            return;
        }
        const found = findNode(configTarget?.nodeId);
        if (!found || found.node.type !== "ActionNode") return;
        const before = JSON.stringify(found.node.rule);
        found.node.rule = cloneNode(actionConfigDraft.rule);
        closeNodeConfig();
        fitMode = false;
        render({ preserveView: true });
        if (before !== JSON.stringify(found.node.rule)) markDraft();
    }

    function insertNodeBefore(typeOverride = null) {
        if (editorReadOnly) return;
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !found.parent) return;
        const { node: target, parent, relation } = found;
        if (relation === "decision") return;
        const type = typeOverride || $("#newNodeType").value;
        // 向前插入只支持包裹为串行或并行结构
        if (!D.conversionTypes("SerialNode").includes(type)) return;
        const node = buildNodeScaffold(type);
        if (!node) return;
        // 把 target 作为新结构的第一个分支
        node.branches[0] = target;
        const added = inspectTree(node).nodes - inspectTree(target).nodes;
        const cur = inspectTree(tree).nodes;
        if (cur + added > TREE_LIMITS.maxNodes) {
            openConfirm(`节点总数将达 ${cur + added}，超过编辑器上限 ${TREE_LIMITS.maxNodes}`, { title: "超出编辑器上限", confirmLabel: "知道了" });
            return;
        }
        const projectedDepth = depthOf(idOf(target)) + 1;
        if (projectedDepth > TREE_LIMITS.maxDepth) {
            openConfirm(`插入后嵌套深度将达 ${projectedDepth}，超过编辑器上限 ${TREE_LIMITS.maxDepth}`, { title: "超出编辑器上限", confirmLabel: "知道了" });
            return;
        }
        // 把新结构放回 target 原来的关系边，target 自身的 next 保持不变
        replaceChild(parent, target, relation, node);
        selectedId = idOf(node);
        insertBeforeMode = false;
        $("#inspectorAddPanel").hidden = true;
        fitMode = false;
        render({ preserveView: true });
        markDraft();
        return node;
    }

    async function deleteSelected() {
        if (editorReadOnly) return;
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !canDeleteFlowNode(found.node, found.parent, found.relation)) return;
        const descendants = countFlowNodes(found.node) - 1;
        const nodeName = flowNodeDisplayName(found.node);
        const nodeId = idOf(found.node);
        const canDeleteOnly = canDeleteCurrentOnly(found.node, found.parent, found.relation);
        const message = canDeleteOnly
            ? ""
            : (descendants
                ? `确定删除「${nodeName}」及其 ${descendants} 个流程子节点吗？`
                : `确定删除「${nodeName}」吗？`);
        const choice = await openConfirm(message, {
            title: canDeleteOnly ? "删除节点" : "确认删除",
            confirmLabel: canDeleteOnly ? "删除当前及后续" : "删除",
            alternateLabel: canDeleteOnly ? "仅删除当前节点" : null
        });
        if (!choice) return;
        const current = findNode(nodeId);
        if (!current || !canDeleteFlowNode(current.node, current.parent, current.relation)) return;
        if (choice === "alternate" && canDeleteCurrentOnly(current.node, current.parent, current.relation)) {
            const next = current.node.next;
            replaceChild(current.parent, current.node, current.relation, next);
            selectedId = idOf(next);
        } else {
            detachChild(current.parent, current.node, current.relation);
            selectedId = idOf(current.parent);
        }
        render({ preserveView: true });
        markDraft();
    }

    function toggleCollapse() {
        const found = selectedId ? findNode(selectedId) : null;
        if (!found) return;
        if (!executionEdges(found.node).length) return;
        setCollapsed(found.node, !isCollapsed(found.node));
        render({ preserveView: true });
    }

    function updateFoldAllButton() {
        let collapsibleCount = 0;
        let collapsedCount = 0;
        walk(tree, (node) => {
            if (!executionEdges(node).length) return;
            collapsibleCount++;
            if (isCollapsed(node)) collapsedCount++;
        });
        const expand = collapsedCount > 0;
        const button = $("#foldAllButton");
        button.disabled = collapsibleCount === 0;
        button.dataset.mode = expand ? "expand" : "collapse";
        button.setAttribute("aria-label", expand ? `展开全部折叠节点，共 ${collapsedCount} 个` : "折叠全部节点");
        button.querySelector("span").textContent = expand ? "展开全部" : "折叠全部";
        button.querySelector(".fold-all-collapse-icon").toggleAttribute("hidden", expand);
        button.querySelector(".fold-all-expand-icon").toggleAttribute("hidden", !expand);
    }

    function flowStatusKey(status) {
        if (status === 1) return "published";
        if (status === 2) return "disabled";
        if (status === 3) return "historical";
        return "draft";
    }

    function flowStatusLabel(status) {
        if (status === 1) return "当前发布";
        if (status === 2) return "已停用";
        if (status === 3) return "历史版本";
        return "草稿";
    }

    function syncFlowHeader() {
        const textEl = $("#docStatusText");
        const pill = $("#docStatus");
        const draftActions = $("#flowDraftActions");
        const save = $("#saveDraftButton");
        const publish = $("#publishFlowButton");
        const createVersion = $("#createVersionButton");
        const test = $("#testFlowButton");
        const versionSelect = $("#flowVersionSelect");
        const off = savingFlow || switchingVersion || flowLoadFailed;

        if (textEl) {
            textEl.textContent = dirty ? "未保存"
            : docStatus === "published" ? "当前发布"
                : docStatus === "disabled" ? "已停用"
                    : docStatus === "historical" ? "历史版本" : "已保存";
        }
        if (pill) {
            pill.classList.toggle("status-formal", !dirty && docStatus === "published");
            pill.classList.toggle("status-disabled", !dirty && docStatus === "disabled");
            pill.classList.toggle("status-historical", !dirty && docStatus === "historical");
        }
        if (draftActions) draftActions.hidden = editorReadOnly;
        if (save) {
            save.textContent = pendingFlowAction === "save" ? "保存中…" : "保存";
            save.title = dirty ? "保存当前修改" : "当前没有未保存修改";
            save.disabled = off || editorReadOnly || !dirty;
        }
        if (publish) {
            publish.textContent = pendingFlowAction === "publish" ? "发布中…" : "发布";
            publish.title = dirty ? "保存当前修改并发布" : "发布当前草稿";
            publish.disabled = off || editorReadOnly;
        }
        if (createVersion) {
            createVersion.hidden = !flowMeta || !editorReadOnly;
            createVersion.textContent = pendingFlowAction === "create" ? "处理中…" : "修改";
            createVersion.title = flowMeta
                ? `修改 V${flowMeta.version}（将创建新草稿）`
                : "修改当前版本（将创建新草稿）";
            createVersion.disabled = off || !flowMeta || !editorReadOnly;
        }
        if (test) test.disabled = off || !flowMeta;
        if (versionSelect) versionSelect.disabled = off || !flowMeta;
    }

    function markDraft() {
        if (editorReadOnly) return;
        if (executedPaths.size) {
            executedPaths.clear();
            treeRoot.querySelector("#executionTrace")?.remove();
            treeRoot.querySelectorAll(".trace-node").forEach((node) => node.classList.remove("trace-node"));
        }
        $("#testResult").hidden = true;
        dirty = true;
        syncFlowHeader();
    }

    function pulseStatus() {
        const pill = $("#docStatus");
        if (pill) { pill.classList.remove("status-pulse"); void pill.offsetWidth; pill.classList.add("status-pulse"); }
    }

    function syncReadonlyControls() {
        document.body.classList.toggle("editor-readonly", editorReadOnly);

        if (editorReadOnly) {
            document.querySelectorAll("#inspectorForm input, #inspectorForm select, #inspectorForm textarea, #inspectorForm button")
                .forEach((control) => { control.disabled = true; });
            document.querySelectorAll("#nodeConfigDialog input, #nodeConfigDialog select")
                .forEach((control) => { control.disabled = true; });
        }
        const viewRule = $("#editNodeButton");
        if (viewRule) viewRule.disabled = false;
        const closeNodeConfigButton = $("#nodeConfigCloseButton");
        const confirmNodeConfigButton = $("#nodeConfigConfirmButton");
        if (closeNodeConfigButton) closeNodeConfigButton.disabled = false;
        if (confirmNodeConfigButton) confirmNodeConfigButton.disabled = false;

        if (editorReadOnly) {
            document.querySelectorAll("#ruleDialog input, #ruleDialog select, #ruleDialog textarea, #ruleDialog button")
                .forEach((control) => { control.disabled = true; });
        }
        const closeRule = $("#ruleDialogCloseButton");
        const cancelRule = $("#ruleDialogCancelButton");
        if (closeRule) {
            closeRule.disabled = false;
            closeRule.textContent = editorReadOnly ? "关闭" : "保存";
        }
        if (cancelRule) {
            cancelRule.disabled = false;
            cancelRule.hidden = editorReadOnly;
        }
        syncFlowHeader();
    }

    function applyEditorMode() {
        const nextReadOnly = Boolean(flowMeta && flowMeta.status !== 0);
        if (editorReadOnly && !nextReadOnly) {
            document.querySelectorAll("#inspectorForm input, #inspectorForm select, #inspectorForm textarea, #inspectorForm button, "
                    + "#ruleDialog input, #ruleDialog select, #ruleDialog textarea, #ruleDialog button, "
                    + "#nodeConfigDialog input, #nodeConfigDialog select")
                .forEach((control) => { control.disabled = false; });
        }
        const closeInspectorEdit = nextReadOnly && Boolean(inspectorEditingField);
        if (closeInspectorEdit) inspectorEditingField = null;
        editorReadOnly = nextReadOnly;
        if (closeInspectorEdit) updateInspector();
        syncReadonlyControls();
    }

    function parseDataPathSegments(path) {
        const segments = [];
        const pattern = /\.([A-Za-z_$][\w$]*)|\[(\d+)\]|\["((?:\\.|[^"\\])*)"\]|\['((?:\\.|[^'\\])*)'\]/g;
        for (const match of path.matchAll(pattern)) {
            if (match[1] != null) segments.push(match[1]);
            else if (match[2] != null) segments.push(Number(match[2]));
            else if (match[3] != null) segments.push(JSON.parse(`"${match[3]}"`));
            else segments.push(match[4].replace(/\\(['\\])/g, "$1"));
        }
        return segments;
    }

    function collectTestDataPaths(root) {
        const paths = { target: [], context: [] };
        const seen = { target: new Set(), context: new Set() };
        const collect = (source) => {
            if (typeof source !== "string" || !source) return;
            const pattern = /(\$\$|\$)((?:\.[A-Za-z_$][\w$]*|\[(?:\d+|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')\])+)/g;
            for (const match of source.matchAll(pattern)) {
                const tail = source.slice((match.index || 0) + match[0].length);
                if (/^\s*\(/.test(tail)) continue;
                const segments = parseDataPathSegments(match[2]);
                if (!segments.length) continue;
                const bucket = match[1] === "$$" ? "context" : "target";
                const key = JSON.stringify(segments);
                if (seen[bucket].has(key)) continue;
                seen[bucket].add(key);
                paths[bucket].push(segments);
            }
        };
        walk(root, (node) => {
            if (!T.hasArgs(node)) return;
            collect(node.expr);
            const definition = node.type === "AtomicNode"
                ? actionDefinitionById(node.expr)
                : ruleDefinitionById(node.expr);
            collect(definition?.expression);
            collect(node.args);
        });
        return paths;
    }

    function buildTestJsonSkeleton(paths) {
        const root = {};
        paths.forEach((segments) => {
            if (segments.some((segment) => ["__proto__", "prototype", "constructor"].includes(segment))) {
                return;
            }
            let current = root;
            segments.forEach((segment, index) => {
                const last = index === segments.length - 1;
                if (last) {
                    if (current[segment] === undefined) current[segment] = null;
                    return;
                }
                const nextContainer = typeof segments[index + 1] === "number" ? [] : {};
                const existing = current[segment];
                if (!existing || typeof existing !== "object"
                    || Array.isArray(existing) !== Array.isArray(nextContainer)) {
                    current[segment] = nextContainer;
                }
                current = current[segment];
            });
        });
        return root;
    }

    function isEmptyJsonObject(input) {
        try {
            const value = JSON.parse(input.value.trim() || "{}");
            return value && typeof value === "object" && !Array.isArray(value)
                && Object.keys(value).length === 0;
        } catch (error) {
            return false;
        }
    }

    function populateTestInputsFromTree() {
        const paths = collectTestDataPaths(tree);
        const targetInput = $("#testTargetInput");
        if (isEmptyJsonObject(targetInput) && paths.target.length) {
            targetInput.value = formatJson(buildTestJsonSkeleton(paths.target));
        }
    }

    function setTestInputError(input, invalid) {
        input.setAttribute("aria-invalid", String(invalid));
    }

    function setTestPanelOpen(open) {
        testPanelOpen = open;
        if (open) {
            populateTestInputsFromTree();
            setInspectorCollapsed(false);
        }
        updateInspector();
        if (open) requestAnimationFrame(() => $("#testTargetInput").focus());
    }

    function formatJson(value) {
        const json = JSON.stringify(value, null, 2);
        return json === undefined ? String(value) : json;
    }

    function parseTestJson(input, label, objectOnly = false) {
        let value;
        try {
            value = JSON.parse(input.value.trim() || "{}");
        } catch (error) {
            setTestInputError(input, true);
            throw new Error(`${label} JSON 格式错误：${error.message}`);
        }
        if (objectOnly && (!value || typeof value !== "object" || Array.isArray(value))) {
            setTestInputError(input, true);
            throw new Error(`${label} 必须是 JSON 对象`);
        }
        setTestInputError(input, false);
        return value;
    }

    function setTestBusy(busy) {
        testRunning = busy;
        const button = $("#runTestButton");
        button.disabled = busy;
        button.querySelector("span").textContent = busy ? "执行中…" : "执行测试";
    }

    function showTestFailure(message) {
        executedPaths.clear();
        render({ preserveView: true });
        $("#testResult").hidden = false;
        $("#testResult").classList.add("is-error");
        $("#testResultStatus").textContent = "执行失败";
        $("#testResultValue").textContent = message;
        $("#testContextValue").textContent = "未返回";
        $("#testPathList").innerHTML = "";
    }

    async function runFlowTest() {
        if (testRunning || !flowMeta || !window.MosikaApi) return;
        if (incompleteTreeGate("测试") || requiredParamGate()) return;
        materializeParamDefaults(tree);
        let target;
        try {
            target = parseTestJson($("#testTargetInput"), "输入数据");
        } catch (error) {
            showTestFailure(error.message);
            return;
        }
        setTestBusy(true);
        try {
            const response = await window.MosikaApi.tryFlow({
                namespace: flowMeta.namespace,
                ruleTree: JSON.stringify(T.serialize(tree)),
                target,
                context: {}
            });
            executedPaths.clear();
            (response.executedPaths || []).forEach((path) => executedPaths.add(path));
            collapsedIds.clear();
            render({ preserveView: true });

            $("#testResult").hidden = false;
            $("#testResult").classList.remove("is-error");
            $("#testResultStatus").textContent = "执行完成";
            $("#testResultValue").textContent = formatJson(response.result?.result);
            $("#testContextValue").textContent = formatJson(response.context || {});
            const paths = flowPathMap();
            $("#testPathList").innerHTML = (response.executedPaths || []).map((path) => {
                const node = paths.get(path);
                return `<li><span>${escapeText(node ? flowNodeDisplayName(node) : "未知节点")}</span></li>`;
            }).join("");
        } catch (error) {
            showTestFailure(error.message);
        } finally {
            setTestBusy(false);
        }
    }

    function incompleteTreeGate(action) {
        const problems = collectViolations();
        if (!problems.length) return false;
        openValidateDialog(problems, action);
        return true;
    }

    function requiredParamGate() {
        const missing = missingRequiredInRule(tree);
        if (!missing.length) return false;
        const names = [...new Set(missing.flatMap((item) => item.params))];
        openConfirm(`请先修正规则参数：${names.join("、")}`,
            { title: "参数不完整或类型不符", confirmLabel: "知道了" });
        return true;
    }

    // 保存到后端：过滤瞬时态后 serialize(tree) → 草稿走 PUT /api/flows/{id}，发布走 POST /publish。
    async function saveToBackend(makeFormal) {
        if (savingFlow || editorReadOnly) return false;
        if (incompleteTreeGate(makeFormal ? "发布" : "保存") || requiredParamGate()) return false;
        materializeParamDefaults(tree);
        if (!flowMeta || !window.MosikaApi) {
            docStatus = makeFormal ? "published" : "draft";
            dirty = false;
            syncFlowHeader(); pulseStatus();
            return true;
        }
        savingFlow = true;
        pendingFlowAction = makeFormal ? "publish" : "save";
        syncFlowHeader();
        try {
            const payload = {
                flowId: flowMeta.flowId, namespace: flowMeta.namespace,
                name: flowMeta.name, description: flowMeta.description,
                ruleTree: JSON.stringify(T.serialize(tree)), version: flowMeta.version
            };
            const updated = makeFormal
                ? await window.MosikaApi.publishFlow(flowMeta.flowId, payload)
                : await window.MosikaApi.updateFlow(flowMeta.flowId, payload);
            if (updated) {
                flowMeta.version = updated.version;
                flowMeta.name = updated.name;
                flowMeta.description = updated.description;
                flowMeta.status = updated.status;
            }
            docStatus = flowStatusKey(flowMeta.status);
            dirty = false;
            applyEditorMode();
            try {
                await refreshFlowVersions(flowMeta.version);
            } catch (error) {
                console.error("刷新版本列表失败", error);
            }
            syncFlowHeader(); pulseStatus();
            return true;
        } catch (e) {
            openConfirm(`${makeFormal ? "发布" : "保存"}失败：${e.message}`,
                { title: makeFormal ? "发布失败" : "保存失败", confirmLabel: "知道了" });
            return false;
        } finally {
            savingFlow = false;
            pendingFlowAction = null;
            syncFlowHeader();
        }
    }

    function saveDraft() { saveToBackend(false); }

    function collectViolations() {
        const problems = [];
        walk(tree, (node) => {
            const name = flowNodeDisplayName(node);
            D.validationIssues(node).forEach((reason) => {
                problems.push({ id: idOf(node), name, reason });
            });
            return true;
        });
        return problems;
    }

    function openValidateDialog(problems, action = "继续") {
        $("#validateSummary").textContent = `发现 ${problems.length} 处结构问题，修正后才能${action}：`;
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

    async function promoteToFormal() {
        if (editorReadOnly) return;
        if (incompleteTreeGate("发布") || requiredParamGate()) return;
        {
            const version = flowMeta?.version;
            const saveAndPublish = dirty;
            const confirmed = await openConfirm(
                saveAndPublish
                    ? (version
                        ? `当前有未保存修改，确定保存并发布 V${version} 吗？发布后该版本不可再修改。`
                        : "当前有未保存修改，确定保存并发布吗？")
                    : (version ? `确定发布 V${version} 吗？发布后该版本不可再修改。` : "确定发布当前版本吗？"),
                {
                    title: saveAndPublish ? "保存并发布" : "发布版本",
                    confirmLabel: saveAndPublish ? "保存并发布" : "发布"
                });
            if (confirmed) saveToBackend(true);
            return;
        }
    }

    function revealNode(id) {
        const path = [];
        const locate = (node) => {
            path.push(node);
            if (idOf(node) === id) return true;
            for (const edge of executionEdges(node)) { if (locate(edge.node)) return true; }
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
        const addButton = event.target.closest("[data-add-parent-id][data-flow-relation]");
        if (addButton) {
            event.stopPropagation();
            selectNode(addButton.dataset.addParentId);
            startAddConfig(addButton.dataset.flowRelation, addButton.dataset.addParentId);
            return;
        }
        const shell = event.target.closest(".node-shell");
        if (!shell) return;
        const nextId = shell.dataset.nodeId;
        if (nextId !== selectedId && !confirmDiscardInspectorEdit()) return;
        selectNode(nextId);
        const action = event.target.closest("[data-action]")?.dataset.action;
        if (action === "collapse") { toggleCollapse(); return; }
    });
    treeRoot.addEventListener("dblclick", (event) => {
        const shell = event.target.closest(".node-shell");
        if (!shell) return;
        const nodeId = shell.dataset.nodeId;
        const node = findNode(nodeId)?.node;
        if (D.isLocallyConfigurable(node)) openLocalNodeConfig(nodeId);
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
        config: '<svg viewBox="0 0 24 24"><path d="M4 20h4L18.5 9.5a2 2 0 0 0-2.83-2.83L5 17v3z"/></svg>',
        trash: '<svg viewBox="0 0 24 24"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/></svg>',
    };
    const contextMenu = document.getElementById("nodeContextMenu");
    function contextItemsFor(found) {
        const node = found.node;
        const items = [];
        if (editorReadOnly) {
            if (isConditionOwner(node)) {
                items.push({ label: node.type === "LoopNode" ? "查看循环条件" : "查看规则", icon: "rule", kind: "normal", onClick: () => openRuleDialog(idOf(node)) });
            }
            return items;
        }
        flowNodeOperations(node).forEach((op, i) => {
            items.push({ label: op.label, icon: "add", kind: i === 0 ? "hot" : "normal", onClick: () => startAddConfig(op.relation) });
        });
        if (D.isLocallyConfigurable(node)) {
            items.push({ label: isLocallyUnconfigured(node) ? "配置此节点" : "编辑配置", icon: "config", kind: "normal", onClick: () => openLocalNodeConfig(idOf(node)) });
        }
        if (node.type !== "TreeNode") items.push({ label: "向前插入", icon: "insert", kind: "normal", onClick: () => openInsertPanel() });
        if (node.type !== "TreeNode") {
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
        if (event.button !== 0 || event.target.closest(".node-shell, button, input, select, textarea")) return;
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
    $("#foldAllButton").addEventListener("click", (event) => {
        if (event.currentTarget.dataset.mode === "expand") {
            collapsedIds.clear();
        } else {
            walk(tree, (node) => {
                if (executionEdges(node).length) setCollapsed(node, true);
            });
        }
        render({ preserveView: true });
    });
    function setInspectorCollapsed(collapsed) {
        document.querySelector(".workspace").classList.toggle("inspector-collapsed", collapsed);
        $("#inspectorExpand").hidden = !collapsed;
        if (fitMode) requestAnimationFrame(fitTree);
    }
    $("#inspectorCollapse").addEventListener("click", () => setInspectorCollapsed(true));
    $("#inspectorExpand").addEventListener("click", () => setInspectorCollapsed(false));
    $("#testFlowButton").addEventListener("click", () => setTestPanelOpen(!testPanelOpen));
    $("#testPanelClose").addEventListener("click", () => setTestPanelOpen(false));
    $("#testTargetInput").addEventListener("input", (event) => setTestInputError(event.target, false));
    $("#runTestButton").addEventListener("click", runFlowTest);
    $("#saveDraftButton").addEventListener("click", saveDraft);
    $("#publishFlowButton").addEventListener("click", promoteToFormal);
    $("#createVersionButton").addEventListener("click", createDraftFromCurrentVersion);
    $("#flowVersionSelect").addEventListener("change", (event) => {
        loadFlowVersion(Number(event.target.value));
    });
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
        if (selectedId) openLocalNodeConfig(selectedId);
    });
    const inspectorFieldClick = (event) => {
        if (event.target.closest("[data-rule-config]")) {
            if (selectedId) openLocalNodeConfig(selectedId);
            return;
        }
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
    const onFlowArgInput = (event) => {
        if (editorReadOnly) return;
        if (!event.target.classList.contains("arg-control")) return;
        const node = selectedId ? findNode(selectedId)?.node : null;
        if (!node) return;
        const container = $("#nodeDetailFields");
        if (commitArgsFromInputs(node, container)) markDraft();
        refreshArgMarks(node, container);
    };
    $("#nodeDetailFields").addEventListener("input", onFlowArgInput);
    $("#nodeDetailFields").addEventListener("change", onFlowArgInput);
    $("#inspectorHeading").addEventListener("click", inspectorFieldClick);
    $("#inspectorHeading").addEventListener("keydown", inspectorFieldKeydown);

    // 模板参数录入：就地写回选中规则节点的 args，不重渲染以保留输入焦点。
    const onRuleArgInput = (event) => {
        if (editorReadOnly) return;
        if (!event.target.classList.contains("arg-control")) return;
        const node = currentEditingNode();
        if (!node) return;
        const container = $("#ruleEditorParamsField");
        if (commitArgsFromInputs(node, container)) markDraft();
        refreshArgMarks(node, container);
    };
    $("#ruleEditorParamsField").addEventListener("input", onRuleArgInput);
    $("#ruleEditorParamsField").addEventListener("change", onRuleArgInput);
    $("#ruleDialogCloseButton").addEventListener("click", () => {
        if (editorReadOnly) {
            ruleDialogCommitted = true;
            ruleDialog.close();
            return;
        }
        const judge = currentRuleJudge();
        if (!judge || !conditionRule(judge)) {
            openConfirm("请先配置判断条件", { title: "节点尚未完成", confirmLabel: "知道了" });
            return;
        }
        if (judge.type === "LoopNode") {
            const max = Number(ruleDialogLoopMaxDraft);
            if (ruleDialogLoopMaxDraft === "" || !Number.isInteger(max) || max < 0) {
                $("#ruleDialogLoopMax").focus();
                openConfirm("最大循环次数必须是非负整数", { title: "节点尚未完成", confirmLabel: "知道了" });
                return;
            }
            judge.max = max;
        }
        const missing = missingRequiredInRule(judge ? conditionRule(judge) : null);
        if (missing.length) {
            ruleSelectedId = idOf(missing[0].node);
            renderRuleDialog();
            const names = [...new Set(missing.flatMap((m) => m.params))];
            openConfirm(`请先修正规则参数：${names.join("、")}`, { title: "参数不完整或类型不符", confirmLabel: "知道了" });
            return;
        }
        ruleDialogCommitted = true;
        ruleDialog.close();
    });
    $("#ruleDialogCancelButton").addEventListener("click", () => { ruleDialogCommitted = false; ruleDialog.close(); });
    $("#ruleEditorDeleteButton").addEventListener("click", () => { deleteSelectedRule(); });
    $("#ruleAddCancelButton").addEventListener("click", cancelRuleBuilder);
    $("#ruleAddConfirmButton").addEventListener("click", confirmRuleBuilder);
    $("#ruleAddRowButton").addEventListener("click", appendRuleBuilderRow);
    $("#ruleEditorAddRuleButton").addEventListener("click", openCompositeBuilder);
    $("#ruleCompositeAddRow").addEventListener("click", appendRuleBuilderRow);
    $("#ruleCompositeConfirmButton").addEventListener("click", confirmRuleBuilder);

    const onRuleBuilderArgInput = (event) => {
        if (!event.target.classList.contains("arg-control")) return false;
        const wrap = event.target.closest("[data-rule-builder-params]");
        if (!wrap) return false;
        const index = Number(wrap.dataset.ruleBuilderParams);
        const row = ruleBuilderState?.rows[index];
        if (!Number.isInteger(index) || !row) return false;
        const staging = stagingRuleNode(row);
        commitArgsFromInputs(staging, wrap);
        row.args = staging.args || "";
        refreshArgMarks(staging, wrap);
        return true;
    };

    const onRuleBuilderRowsChange = (event) => {
        if (onRuleBuilderArgInput(event)) return;
        const select = event.target.closest("[data-rule-builder-select]");
        if (!select) return;
        const row = ruleBuilderState?.rows[Number(select.dataset.ruleBuilderSelect)];
        if (!row) return;
        if (row.ruleId !== select.value) row.args = "";
        row.ruleId = select.value;
        renderRuleBuilder();
    };
    const onRuleBuilderRowsClick = (event) => {
        const button = event.target.closest("[data-rule-builder-remove]");
        if (button) removeRuleBuilderRow(Number(button.dataset.ruleBuilderRemove));
    };
    const onRuleBuilderLogicChange = (event) => {
        if (!ruleBuilderState || event.target.name !== "activeRuleBuilderLogic") return;
        ruleBuilderState.operator = event.target.value;
        renderRuleBuilderLogic();
    };
    const onRuleBuilderBoundsInput = (event) => {
        if (!ruleBuilderState) return;
        if (event.target.matches("[data-rule-builder-min]")) ruleBuilderState.minHits = event.target.value;
        if (event.target.matches("[data-rule-builder-max]")) ruleBuilderState.maxHits = event.target.value;
    };
    ["#ruleAddRows", "#ruleCompositeRows"].forEach((selector) => {
        $(selector).addEventListener("input", onRuleBuilderArgInput);
        $(selector).addEventListener("change", onRuleBuilderRowsChange);
        $(selector).addEventListener("click", onRuleBuilderRowsClick);
    });
    ["#ruleAddLogicField", "#ruleCompositeLogicField"].forEach((selector) => {
        $(selector).addEventListener("change", onRuleBuilderLogicChange);
        $(selector).addEventListener("input", onRuleBuilderBoundsInput);
    });
    $("#ruleAddDialog").addEventListener("close", () => {
        if (ruleBuilderState?.mode === "dialog") ruleBuilderState = null;
    });
    $("#ruleEditorSubrules").addEventListener("change", (event) => {
        const select = event.target.closest("[data-subrule-select]");
        if (!select) return;
        const node = currentEditingNode();
        if (!node) return;
        const idx = Number(select.dataset.subruleSelect);
        const target = node.type === "BooleanNode" ? node : node.rules[idx];
        if (target && target.type === "BooleanNode") {
            if (target.expr !== select.value) target.args = "";
            target.expr = select.value;
            renderRuleTree();
            renderRuleEditorParams(target);
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
    $("#ruleEditorLogicField").addEventListener("change", (event) => {
        if (event.target.id === "ruleLogicSelect") {
            $("#ruleMatchBounds").hidden = event.target.value !== "some";
        }
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
        if (ruleJudgeDraft) {
            if (committed) ruleDraftCommit?.(ruleJudgeDraft);
        } else if (!committed && ruleDialogSnapshot && ruleJudgeId) {
            const found = findNode(ruleJudgeId);
            if (found) setConditionRule(found.node, ruleDialogSnapshot.rule);
        }
        ruleDialogSnapshot = null;
        ruleDialogCommitted = false;
        ruleDialogLoopMaxDraft = "";
        ruleJudgeId = null;
        ruleJudgeDraft = null;
        ruleDraftCommit = null;
        ruleSelectedId = null;
        hideRulePopover();
        render({ preserveView: true });
        if (committed) markDraft();
    });
    $("#ruleDialogLoopMax").addEventListener("input", (event) => {
        ruleDialogLoopMaxDraft = event.target.value;
    });

    document.addEventListener("pointerdown", (event) => {
        if (event.target.closest(".node-shell")) return;
        document.activeElement?.closest?.(".node-shell")?.blur();
    }, true);

    $("#confirmAddButton").addEventListener("click", () => {
        // 添加面板现仅用于「向前插入（包裹为结构）」
        if (insertBeforeMode) insertNodeBefore();
    });
    // 「配置节点」弹窗
    $("#nodeConfigConfirmButton").addEventListener("click", confirmNodeConfig);
    $("#nodeConfigType").addEventListener("change", updateNodeConfigStep);
    $("#nodeConfigCancelButton").addEventListener("click", closeNodeConfig);
    $("#nodeConfigCloseButton").addEventListener("click", closeNodeConfig);
    document.getElementById("nodeConfigDialog").addEventListener("cancel", (e) => { e.preventDefault(); closeNodeConfig(); });
    $("#actionConfigFields").addEventListener("change", (event) => {
        if (!actionConfigDraft) return;
        if (event.target.id === "actionConfigDefinitionSelect") {
            const ruleId = event.target.value;
            if (ruleId && actionConfigDraft.rule?.expr !== ruleId) actionConfigDraft = make.action(ruleId);
            renderActionConfig();
            return;
        }
        if (event.target.classList.contains("arg-control")) {
            commitArgsFromInputs(actionConfigDraft, $("#actionConfigParams"));
            refreshArgMarks(actionConfigDraft, $("#actionConfigParams"));
        }
    });
    $("#actionConfigFields").addEventListener("input", (event) => {
        if (!actionConfigDraft || !event.target.classList.contains("arg-control")) return;
        commitArgsFromInputs(actionConfigDraft, $("#actionConfigParams"));
        refreshArgMarks(actionConfigDraft, $("#actionConfigParams"));
    });
    document.addEventListener("click", (event) => {
        const option = event.target.closest(".rule-picker-option");
        if (option) chooseDefinitionOption(option);
    });
    document.addEventListener("input", (event) => {
        if (event.target.classList.contains("rule-picker-filter")) filterDefinitionPicker(event.target);
    });
    document.addEventListener("focusin", (event) => {
        if (!event.target.classList.contains("rule-picker-filter")) return;
        renderDefinitionPicker(event.target, definitionsForPicker(event.target), "", true);
    });
    document.addEventListener("keydown", (event) => {
        if (handleDefinitionPickerKeydown(event)) event.stopPropagation();
    }, true);
    document.addEventListener("pointerdown", (event) => {
        document.querySelectorAll(".rule-picker-filter[aria-expanded='true']").forEach((filterInput) => {
            if (!filterInput.closest(".rule-picker").contains(event.target)) closeDefinitionPicker(filterInput);
        });
    }, true);
    $("#newNodeCancelButton").addEventListener("click", closeAddPanel);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#inspectorAddPanel").hidden) closeAddPanel();
        const editing = event.target.matches("input, select, textarea");
        if (!editorReadOnly && !editing && ruleDialog.open && (event.key === "Delete" || event.key === "Backspace")) deleteSelectedRule();
        if (!editing && event.key === "0") fitTree();
    });

    const reorderSuppress = { flow: false, rule: false };

    function createSiblingReorder(cfg) {
        const cont = cfg.container;
        const THRESHOLD = 5;
        let st = null;

        function members(parent, relation) {
            if (relation === "rule") return parent.rules || [];
            return parent.branches || [];
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
            order.forEach((node) => list.push(node));
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
        canStart: () => !editorReadOnly,
        axis: "x",
        branchAttr: "data-branch-id",
        shellIdKey: "nodeId",
        findEntry: (id) => findNode(id),
        relationOf: (entry) => entry.relation,
        labelOf: (node) => flowNodeCanvasLabel(node),
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
        canStart: () => !editorReadOnly,
        axis: "y",
        branchAttr: "data-rule-branch-id",
        shellIdKey: "ruleNodeId",
        findEntry: (id) => findRuleNode(id),
        relationOf: () => "rule",
        labelOf: (node) => ruleNodeCanvasLabel(node),
        afterCommit: (ref) => {
            ruleSelectedId = idOf(ref.node);
            renderRuleTree();
            updateRuleEditor();
            render({ preserveView: true });
        }
    });

    new ResizeObserver(() => { if (fitMode) fitTree(); }).observe(viewport);
    render({ preserveView: false });
    syncFlowHeader();

    // 对外桥接层，供页面接线和浏览器交互测试使用。
    window.__mosikaEditor = {
        getTreeJson() { return T.serialize(tree); },
        loadTreeJson(json, opts) { loadTree(json, opts || {}); },
        setDocStatus(status) {
            docStatus = status === "formal" ? "published" : status;
            syncFlowHeader();
        },
        markDraft
    };

    function showLoadError(message) {
        $("#loadErrorText").textContent = message;
        $("#loadErrorBanner").hidden = false;
    }

    function renderFlowVersions(selectedVersion = flowMeta?.version) {
        const picker = $("#flowVersionPicker");
        const select = $("#flowVersionSelect");
        if (!picker || !select) return;
        picker.hidden = !flowMeta;
        select.innerHTML = flowVersions.map((flow) =>
            `<option value="${flow.version}" ${flow.version === selectedVersion ? "selected" : ""}>V${flow.version} · ${flowStatusLabel(flow.status)}</option>`
        ).join("");
    }

    async function refreshFlowVersions(selectedVersion = flowMeta?.version) {
        if (!flowMeta || !window.MosikaApi) return;
        flowVersions = await window.MosikaApi.listFlowVersions(flowMeta.flowId) || [];
        renderFlowVersions(selectedVersion);
    }

    function updateVersionUrl(version) {
        const url = new URL(window.location.href);
        url.searchParams.set("version", String(version));
        window.history.replaceState(null, "", url);
    }

    function applyLoadedFlow(flow) {
        flowMeta = {
            flowId: flow.flowId,
            namespace: flow.namespace,
            name: flow.name,
            description: flow.description,
            version: flow.version,
            status: flow.status
        };
        applyEditorMode();
        const titleEl = document.querySelector(".canvas-titlebar strong");
        if (titleEl) titleEl.textContent = flow.name || "流程画布";
        const crumbEl = document.getElementById("flowCrumbName");
        if (crumbEl) crumbEl.textContent = flow.name || "规则树编辑器";
        document.title = `${flow.name || "场景"} · Mosika`;
        loadTree(flow.ruleTree);
        docStatus = flowStatusKey(flow.status);
        dirty = false;
        renderFlowVersions(flow.version);
        updateVersionUrl(flow.version);
        syncFlowHeader();
        applyEditorMode();
        syncFlowHeader();
    }

    async function loadFlowVersion(version) {
        if (!flowMeta || !Number.isInteger(version) || version <= 0 || version === flowMeta.version) return;
        const previousVersion = flowMeta.version;
        if (dirty) {
            const confirmed = await openConfirm("当前草稿有未保存修改，确定切换版本吗？", {
                title: "切换版本",
                confirmLabel: "切换"
            });
            if (!confirmed) {
                renderFlowVersions(previousVersion);
                return;
            }
        }
        switchingVersion = true;
        syncFlowHeader();
        try {
            const flow = await window.MosikaApi.getFlow(flowMeta.flowId, version);
            if (!flow) throw new Error("场景版本不存在");
            applyLoadedFlow(flow);
        } catch (error) {
            renderFlowVersions(previousVersion);
            openConfirm(`切换版本失败：${error.message}`, { title: "切换失败", confirmLabel: "知道了" });
        } finally {
            switchingVersion = false;
            syncFlowHeader();
        }
    }

    async function createDraftFromCurrentVersion() {
        if (!flowMeta || savingFlow || switchingVersion) return;
        if (dirty) {
            openConfirm("请先保存当前草稿，再基于该版本创建新版本。",
                { title: "存在未保存修改", confirmLabel: "知道了" });
            return;
        }
        savingFlow = true;
        pendingFlowAction = "create";
        syncFlowHeader();
        try {
            const draft = await window.MosikaApi.createFlowVersion(flowMeta.flowId, flowMeta.version);
            if (!draft) throw new Error("创建草稿版本失败");
            applyLoadedFlow(draft);
            await refreshFlowVersions(draft.version);
            pulseStatus();
        } catch (error) {
            openConfirm(`创建草稿版本失败：${error.message}`,
                { title: "创建失败", confirmLabel: "知道了" });
        } finally {
            savingFlow = false;
            pendingFlowAction = null;
            syncFlowHeader();
        }
    }

    $("#loadErrorRetry").addEventListener("click", () => window.location.reload());

    // ---- 后端接线：规则下拉来自轻量引用接口；?flowId 打开真实规则流程 ----
    (async function initBackend() {
        if (!window.MosikaApi) return;
        const flowId = resolveFlowId();
        // 无 flowId = 独立演示，保留内置示例与演示规则定义，不触碰后端。
        if (!flowId) return;
        try {
            const requestedVersionText = new URLSearchParams(window.location.search).get("version");
            const requestedVersion = /^[1-9]\d*$/.test(requestedVersionText || "")
                ? Number(requestedVersionText) : undefined;
            const flow = await window.MosikaApi.getFlow(flowId, requestedVersion);
            if (!flow) throw new Error("场景不存在");
            flowMeta = { flowId: flow.flowId, namespace: flow.namespace };
            const [atomics, flows, versions] = await Promise.all([
                window.MosikaApi.listRuleReferences(flow.namespace),
                window.MosikaApi.listFlowReferences(flow.namespace),
                window.MosikaApi.listFlowVersions(flow.flowId)
            ]);
            flowVersions = versions || [];
            applyRuleDefinitions(atomics, flows.filter((candidate) => candidate.flowId !== flow.flowId));
            $("#loadErrorBanner").hidden = true;
            if (window.MosikaNs) {
                window.MosikaNs.adopt(flow.namespace);
                const nsEl = document.getElementById("flowCrumbNs");
                const nsWrap = document.getElementById("flowCrumbNsWrap");
                if (nsEl && flow.namespace) {
                    nsEl.textContent = flow.namespace;
                    if (nsWrap) nsWrap.hidden = false;
                }
                const backEl = document.getElementById("backToFlows");
                if (backEl) backEl.setAttribute("href", window.MosikaNs.link("/scenes"));
            }
            applyLoadedFlow(flow);
        } catch (e) {
            console.error("加载场景失败", e);
            // 只读错误态：禁用保存/发布，避免把空白画布当成真实流程覆盖后端。
            flowLoadFailed = true;
            syncFlowHeader();
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
        backToFlows.addEventListener("click", async (event) => {
            if (!dirty) return;
            event.preventDefault();
            const confirmed = await openConfirm("当前修改尚未保存，确定返回场景列表吗？", {
                title: "返回场景列表",
                confirmLabel: "返回"
            });
            if (confirmed) window.location.assign(backToFlows.href);
        });
    }

})();
