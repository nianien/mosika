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
    const { childEdges, childNodes, executionEdges, make } = T;

    const TYPES = {
        T: { name: "根节点", kind: "root", short: "开始", help: "规则树入口，仅承担根节点定位。" },
        S: { name: "串行节点", kind: "structure", short: "串", help: "子节点按顺序执行，顺序由树中从左到右的位置表达。" },
        P: { name: "并行节点", kind: "structure", short: "并", help: "多个子节点并发执行；结构节点本身不承载业务结果。" },
        D: { name: "分支节点", kind: "structure", short: "分", help: "按顺序检查各条件，多选一并在首个命中后停止，最后可设置默认分支。" },
        C: { name: "条件节点", kind: "structure", short: "条件", help: "引用一棵可递归嵌套的布尔规则树，命中后执行其后续流程。" },
        L: { name: "逻辑", kind: "structure", short: "与", help: "使用“与”或“或”组合两个及以上纯规则子节点。" },
        H: { name: "命中数", kind: "structure", short: "H", help: "表达 some(min,max,...)；例如至少命中 2 项。" },
        R: { name: "动作规则", kind: "action", short: "R", help: "动作节点内部引用的原子规则。" },
        B: { name: "条件规则", kind: "condition", short: "B", help: "条件节点内部引用的可取反原子规则。" },
        A: { name: "动作节点", kind: "action", short: "动作", help: "引用一条后台执行动作或命名复合规则，next 表示无条件后继。" },
        PH: { name: "待配置", kind: "placeholder", short: "待配置", help: "待配置的占位节点，点击选择其类型与引用规则；存在占位时不能保存/生效。" }
    };

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
    let ruleSelectedId = null;
    let ruleAddRuleIds = [];
    // 暂存面板每行已录入的 $args（JSON 文本），与 ruleAddRuleIds 同下标；确认添加时写入新建节点
    let ruleAddArgs = [];
    // 后端接线：当前打开的 flow 元数据（null=独立演示，无法保存）。
    let flowMeta = null;
    let savingFlow = false;
    let dirty = false;
    let testPanelOpen = false;
    let testRunning = false;
    const executedPaths = new Set();

    const FLOW_TYPES = T.FLOW_TYPES;
    // 各真实递归边允许的可创建节点类型。
    const RELATIONS = {
        next: { types: FLOW_TYPES },
        matched: { types: FLOW_TYPES },
        branch: { types: FLOW_TYPES },
        decision: { types: ["C"] },
        default: { types: ["A", "S", "P", "D"] }
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

    // 条件节点的规则子树与命中流程拆分
    function conditionRule(node) { return node.type === "C" ? (node.rule || null) : null; }

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
        const alias = node.name?.trim();
        if (alias) return alias;
        if (node.type === "B") {
            return ruleDefinitionById(node.expr)?.desc || "未命名规则";
        }
        return ["L", "H"].includes(node.type) ? "复合规则" : TYPES[node.type]?.name || "未命名规则";
    }

    function isStructuralFlowType(type) {
        return ["T", "S", "P", "D"].includes(type);
    }

    function isCompositeReference(node) {
        return node.type === "A" && /^f[1-9]\d*$/.test(String(node.rule?.expr || ""));
    }

    function flowNodeDisplayName(node) {
        if (node.type === "T") return "开始";
        const name = flowNodeName(node);
        if (name) return name;
        if (isStructuralFlowType(node.type)) return TYPES[node.type].name;
        if (node.type === "C") return ruleDisplayName(node);
        if (node.type === "A") {
            const ruleId = node.rule?.expr;
            if (isCompositeReference(node)) return `复合规则 ${ruleId}`;
            return actionDefinitionById(ruleId)?.desc || TYPES[node.type].name;
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
            if (["A", "C"].includes(node.type) && node.rule) modalRuleNodes += countNodes(node.rule);
            const children = executionEdges(node).map((edge) => edge.node);
            const hideChildren = hidden || (node.type !== "C" && isCollapsed(node) && children.length > 0);
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
            for (const edge of childEdges(node)) stack.push([edge.node, depth + 1]);
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

    function availableRelations(parent) {
        if (parent.type === "T") return parent.next ? [] : ["next"];
        const relations = [];
        if (parent.type === "A" && !parent.next) relations.push("next");
        if (parent.type === "C" && !parent.next) relations.push("matched");
        if (["S", "P"].includes(parent.type)) relations.push("branch");
        if (parent.type === "D") {
            relations.push("decision");
            if (!parent.defaultBranch) relations.push("default");
        }
        return relations;
    }

    function flowNodeOperations(node) {
        const available = new Set(availableRelations(node));
        const operations = [];
        const add = (relation, label, dialogTitle) => {
            if (available.has(relation)) operations.push({ relation, label, dialogTitle });
        };
        if (node.type === "T") {
            add("next", "设置入口", "设置入口");
        } else if (node.type === "S" || node.type === "P") {
            add("branch", "添加分支", "添加分支");
        } else if (node.type === "D") {
            add("decision", "添加分支", "添加分支");
            add("default", "设置默认", "设置默认");
        } else if (node.type === "C") {
            add("matched", "添加命中流程", "添加命中流程");
        } else if (node.type === "A") {
            add("next", "添加后续", "添加后续");
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
            const hasDefault = !!parent.defaultBranch;
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
        const edge = childEdges(parent).find((candidate) => candidate.node === node && candidate.relation === relation);
        if (!edge) return;
        if (relation === "branch") {
            parent.branches.splice(edge.index, 1);
        } else if (relation === "decision") {
            const idx = parent.branches.indexOf(node);
            if (idx >= 0) parent.branches.splice(idx, 1);
        } else if (relation === "next") {
            parent.next = node.type === "A" ? node.next : null;
        } else if (relation === "matched") {
            parent.next = null;
        } else if (relation === "default") {
            parent.defaultBranch = null;
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
        if (node.type === "C") return "规则";
        if (node.type !== "H") return type.short;
        const low = node.minHits == null ? "_" : String(node.minHits);
        const high = node.maxHits == null ? "_" : String(node.maxHits);
        if (high === "_" && low !== "_") return `${low}+`;
        if (low === "_") return `≤${high}`;
        if (low === high) return `=${low}`;
        return `${low}…${high}`;
    }

    // ---- 主画布渲染 ----
    function flowChildPath(parentPath, edge) {
        if (edge.relation === "next" || edge.relation === "matched") return `${parentPath}.next`;
        if (edge.relation === "branch" || edge.relation === "decision") {
            return `${parentPath}.branches[${edge.index}]`;
        }
        if (edge.relation === "default") return `${parentPath}.defaultBranch`;
        return parentPath;
    }

    function renderBranch(node, parent = null, relation = null, siblingCount = 1, path = "$") {
        const type = TYPES[node.type] || TYPES.A;
        const reorderable = Boolean(parent && ["branch", "decision"].includes(relation)
            && siblingCount >= 2);
        const expressionValue = node.type === "A" ? node.rule?.expr : "";
        const expression = expressionValue ? `<span class="node-expression">${escapeText(expressionValue)}</span>` : "";
        const executionChildren = executionEdges(node);
        const collapsible = node.type !== "C" && executionChildren.length > 0;
        const collapsed = Boolean(collapsible && isCollapsed(node));
        const visibleEdges = collapsed ? [] : executionChildren;
        const relationCounts = visibleEdges.reduce((counts, edge) => {
            counts.set(edge.relation, (counts.get(edge.relation) || 0) + 1);
            return counts;
        }, new Map());
        const children = visibleEdges.length
            ? `<div class="tree-children">${visibleEdges.map((edge) => renderBranch(
                edge.node, node, edge.relation, relationCounts.get(edge.relation),
                flowChildPath(path, edge))).join("")}</div>` : "";
        const hiddenCount = executionChildren.length;
        const collapsedBadge = collapsed ? `<span class="collapsed-count" title="${hiddenCount} 个分支">${hiddenCount}</span>` : "";
        // 待配置节点（占位 PH，或未选引用规则的 动作/条件/判断）统一以红虚线“待配置”呈现。
        const pending = isUnconfigured(node);
        const title = pending
            ? `＋ ${pendingLabel(node)}`
            : (node.type === "C"
                ? ruleDisplayName(node)
                : (type.kind === "structure" || type.kind === "root"
                    ? compactStructureLabel(node, type)
                    : flowNodeDisplayName(node)));
        const cardKind = pending
            ? `placeholder${node.type === "PH" && node.slot === "decision" ? " placeholder-hex" : ""} node-type-${node.type.toLowerCase()}`
            : `${node.type === "C" ? "condition judge-summary" : type.kind} node-type-${node.type.toLowerCase()}`;
        const cardTitle = pending
            ? `${pendingLabel(node)}（双击配置）`
            : (node.type === "C"
                ? `条件节点 · ${ruleDisplayName(node)}`
                : (node.type === "A"
                    ? `${type.name} · ${flowNodeDisplayName(node)}`
                    : (flowNodeName(node) ? `${type.name} · ${flowNodeName(node)}` : type.name)));
        const jNegated = node.type === "C" && Boolean(conditionRule(node)?.negative);
        const negateBadge = jNegated ? `<span class="negate-badge" title="取反">非</span>` : "";
        const id = idOf(node);
        const nodeShell = `
            <div class="node-shell${collapsible ? " has-fold-toggle" : ""}${executedPaths.has(path) ? " trace-node" : ""}"${reorderable ? ' data-reorderable="1"' : ""} data-node-id="${id}" data-flow-path="${escapeText(path)}" tabindex="0" role="treeitem" aria-selected="${selectedId === id}" aria-expanded="${!collapsed}">
                    <div class="node-card ${cardKind}${jNegated ? " is-negated" : ""}" title="${escapeText(cardTitle)}">
                        <span class="node-title">${escapeText(title || type.short)}</span>
                        ${expression}
                    </div>
                    ${negateBadge}
                    ${collapsible ? `<button class="node-fold-toggle" type="button" data-action="collapse" title="${collapsed ? "展开分支" : "折叠分支"}" aria-label="${collapsed ? "展开" : "收起"}">${collapsed ? "+" : "−"}</button>` : ""}
                    ${collapsedBadge}
                </div>`;
        return `
            <div class="tree-branch" data-branch-id="${id}" data-flow-path="${escapeText(path)}">
                ${nodeShell}
                ${children}
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
        svg.setAttribute("viewBox", `0 0 ${treeRoot.scrollWidth} ${treeRoot.scrollHeight}`);
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
        const stats = canvasStats(tree);
        $("#nodeCount").textContent = `${stats.flowNodes} 个流程节点${stats.modalRuleNodes ? ` · ${stats.modalRuleNodes} 个规则节点在弹窗` : ""}${stats.hiddenNodes ? ` · ${stats.hiddenNodes} 个流程已折叠` : ""}`;
        updateInspector();
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

    function judgeRuleReference(node) {
        const rule = conditionRule(node);
        if (!rule) return "未设置";
        if (rule.type === "B") return definitionReference(RULE_DEFINITIONS, rule.expr);
        return `复合规则 · ${countNodes(rule)} 个规则节点`;
    }

    function inspectorEditableType(type) {
        return ["A", "S", "P", "D", "C"].includes(type);
    }

    // ---- 模板参数（$args）录入 ----
    // 节点引用的单个原子规则ID：A 取内嵌 R，C 取内嵌 B，复合规则流(f*)与复合规则无参
    function nodeReferencedRuleId(node) {
        if (!node) return null;
        if (node.type === "A") return isCompositeReference(node) ? null : (node.rule?.expr || null);
        if (["R", "B"].includes(node.type)) return node.expr || null;
        if (node.type === "C") { const r = conditionRule(node); return r && r.type === "B" ? (r.expr || null) : null; }
        return null;
    }

    // 承载 args 的宿主节点是 A/C 内嵌的 R/B，规则编辑器中的 R/B 则直接作为宿主
    function argsHost(node) {
        if (!node) return null;
        if (node.type === "A") return node.rule?.type === "R" ? node.rule : null;
        if (["R", "B"].includes(node.type)) return node;
        if (node.type === "C") { const r = conditionRule(node); return r && r.type === "B" ? r : null; }
        return null;
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
            if (["R", "B"].includes(node.type)) {
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
            if (!["R", "B"].includes(node.type)) return true;
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
        const location = findNode(nodeId);
        const canWrap = Boolean(["A", "C"].includes(node.type)
            && location?.parent && location.relation !== "decision");
        const typeEditable = ["S", "P"].includes(node.type) || canWrap;
        const typeActive = typeEditable && inspectorEditingField === editKey(nodeId, "type");
        const typeOptions = ["S", "P"].includes(node.type)
            ? ["S", "P"]
            : (canWrap ? [node.type, "S", "P"] : [node.type]);
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
            const selectedRuleId = node.rule?.expr === "∅" ? null : node.rule?.expr;
            ruleField(ACTION_DEFINITIONS, selectedRuleId, flowNodeName(node));
        } else if (node.type === "C") {
            readonlyField("引用规则", judgeRuleReference(node));
        } else if (["S", "P"].includes(node.type)) {
            readonlyField("分支数", `${node.branches.length} 个`);
        } else if (node.type === "D") {
            readonlyField("分支数", `${node.branches.length} 个`);
            readonlyField("默认分支", node.defaultBranch ? "已设置" : "未设置");
        }
        if (node.type === "T") {
            readonlyField("流程入口", node.next ? flowNodeDisplayName(node.next) : "未设置");
        } else if (node.type === "C") {
            readonlyField("命中流程", node.next ? flowNodeDisplayName(node.next) : "未设置");
        }
        if (node.type === "A") parts.push(renderParamsSectionHtml(node));
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
        editButton.hidden = node.type !== "C";
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
        } else if (field === "expression" && node.type === "A") {
            const definition = ACTION_DEFINITIONS.find((candidate) => candidate.ruleId === $("#inspectorDefinitionSelect").value);
            if (definition && node.rule) node.rule.expr = definition.ruleId;
        } else if (field === "expression" && node.type === "C") {
            // 就地设置条件节点的根规则（单个规则），保留原规则名与取反
            const definition = RULE_DEFINITIONS.find((candidate) => candidate.ruleId === $("#inspectorDefinitionSelect").value);
            if (definition) {
                const r = conditionRule(node);
                if (r && r.type === "B") { r.expr = definition.ruleId; }
                else { node.rule = make.atom(definition.ruleId); }
            }
        } else if (field === "type" && ["S", "P"].includes(node.type)) {
            const newType = $("#inspectorTypeSelect").value;
            if (["S", "P"].includes(newType)) node.type = newType;
        } else if (field === "type" && ["A", "C"].includes(node.type)) {
            const newType = $("#inspectorTypeSelect").value;
            if (["S", "P"].includes(newType)) {
                inspectorEditingField = null;
                insertNodeBefore(newType);
                return;
            }
        }
        inspectorEditingField = null;
        render({ preserveView: true });
        if (before !== JSON.stringify(node)) markDraft();
    }

    // 所有节点名称统一写入 NameNode.name
    function setNodeAlias(node, value) {
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

    function confirmDiscardInspectorEdit() { inspectorEditingField = null; return true; }

    // ---- 规则弹窗（在 C 的规则子树上操作）----
    function currentRuleJudge() {
        const found = ruleJudgeId ? findNode(ruleJudgeId) : null;
        return found?.node.type === "C" ? found.node : null;
    }

    // 在当前 C 的规则子树中定位规则节点，返回 {node, parent}，parent 为 L/H 或 C（根规则）
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
        const type = TYPES[node.type] || TYPES.B;
        const reorderable = Boolean(parent && ["L", "H"].includes(parent.type) && (parent.rules?.length || 0) >= 2);
        const ruleExpression = node.type === "L" ? node.expr : (node.type === "B" ? node.expr : "");
        const expression = ruleExpression ? `<span class="node-expression">${escapeText(ruleExpression)}</span>` : "";
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
        if (found.parent?.type === "C") return false;
        if (found.parent?.type === "H" && (found.parent.rules?.length || 0) <= 1) return false;
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
                <span>尚无规则子树</span>
                <span>从这里配置第一条规则</span>
                <button class="primary" id="ruleAddRootButton" type="button">＋ 配置根规则</button>
            </div>`;
        $("#ruleNodeCount").textContent = rule ? `${countNodes(rule)} 个规则节点` : "0 个规则节点";
    }

    function ruleNodeSubrules(node) {
        if (node.type === "B") return [node];
        if (["L", "H"].includes(node.type)) return node.rules.slice();
        return [];
    }

    function subruleRowLabel(child) {
        if (child.type === "B") {
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
            if (child.type !== "B") {
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
        $("#ruleEditorAddRuleButton").hidden = !["B", "L", "H"].includes(node.type);
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
        if (node.type === "B") {
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
            const hasRule = Boolean(currentRuleJudge() && conditionRule(currentRuleJudge()));
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
        renderRuleEditorParams(node);
        $("#ruleNegateField").hidden = false;
        $("#ruleNegateInput").checked = !!node.negative;
        $("#ruleAddPanel").hidden = true;
        $("#ruleEditorFooter").hidden = false;
        const deletable = canDeleteRule(found);
        $("#ruleEditorDeleteButton").disabled = !deletable;
        $("#ruleEditorDeleteButton").title = deletable
            ? ""
            : (found.parent?.type === "C"
                ? "条件节点必须保留根规则，不能删除"
                : "组合规则至少需要保留两个子规则，不能直接删除");
    }

    // 规则弹窗内选中原子规则节点且其引用规则声明了模板参数时渲染参数录入区并绑定到该 BNode 的 args
    function renderRuleEditorParams(node) {
        const field = $("#ruleEditorParamsField");
        if (!field) return;
        if (!node || node.type !== "B") { field.hidden = true; field.innerHTML = ""; return; }
        const html = renderParamsSectionHtml(node);
        field.innerHTML = html;
        field.hidden = !html;
    }

    function currentEditingNode() { return findRuleNode(ruleSelectedId)?.node || null; }

    // 用幸存子规则替换 L 节点，L 是 C 的根规则则替换 C.rule，否则替换父 L/H.rules 中的该项
    function collapseLogicToSurvivor(logicNode, survivor) {
        const found = findRuleNode(idOf(logicNode));
        const parent = found ? found.parent : currentRuleJudge();
        if (!parent) return;
        if (parent.type === "C") {
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
        if (!node || !["B", "L", "H"].includes(node.type)) return;
        compositePromoteFrom = node.type === "B" ? node : null;
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

        if (compositePromoteFrom && node.type === "B") {
            // 单规则转复合：创建 LNode，把原 R 保留为第一个子规则，新选的追加其后
            const lNode = make.logic(logic);
            lNode.name = "";
            // 保留原 R 的名称、取反状态和引用
            const originalR = {
                type: "B", name: node.name || "", expr: node.expr,
                args: node.args || "", negative: node.negative
            };
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
            // parent 可能是 C（rule 边）或 L/H（rules 数组）
            const parent = found.parent;
            if (parent.type === "C") {
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
        if (!found || found.node.type !== "C") return;
        ruleJudgeId = judgeId;
        ruleDialogSnapshot = {
            rule: cloneNode(found.node.rule),
            rollbackOnCancel
        };
        ruleDialogCommitted = false;
        const rule = conditionRule(found.node);
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

    // 暂存行的临时原子规则节点：仅用于复用参数区渲染与回填，不进入规则树
    function stagingRuleNode(index) {
        return { type: "B", expr: ruleAddRuleIds[index] || "", args: ruleAddArgs[index] || "" };
    }

    function renderRuleAddRows(focusIndex = -1) {
        $("#ruleAddRows").innerHTML = ruleAddRuleIds.map((ruleId, index) => {
            const params = renderParamsSectionHtml(stagingRuleNode(index));
            return `
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
            </div>
            ${params ? `<div class="rule-add-params" data-rule-add-params="${index}">${params}</div>` : ""}`;
        }).join("");
        $("#ruleAddLogicField").hidden = ruleAddRuleIds.length <= 1;
        requestAnimationFrame(() => {
            if (focusIndex >= 0) $(`[data-rule-add-select="${focusIndex}"]`)?.focus();
        });
    }

    function appendRuleAddRow() {
        const unused = RULE_DEFINITIONS.find((definition) => !ruleAddRuleIds.includes(definition.ruleId));
        ruleAddRuleIds.push(unused?.ruleId || RULE_DEFINITIONS[0]?.ruleId || "");
        ruleAddArgs.push("");
        renderRuleAddRows(ruleAddRuleIds.length - 1);
    }

    function removeRuleAddRow(index) {
        if (ruleAddRuleIds.length <= 1 || index < 0 || index >= ruleAddRuleIds.length) return;
        ruleAddRuleIds.splice(index, 1);
        ruleAddArgs.splice(index, 1);
        renderRuleAddRows(Math.min(index, ruleAddRuleIds.length - 1));
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
        ruleAddRuleIds = [RULE_DEFINITIONS[0]?.ruleId || ""];
        ruleAddArgs = [""];
        document.querySelector('input[name="ruleAddLogic"][value="&&"]').checked = true;
        $("#ruleEditorFooter").hidden = true;
        $("#ruleAddPanel").hidden = false;
        renderRuleAddRows(0);
    }

    function cancelRuleAdd() {
        ruleAddRuleIds = [];
        ruleAddArgs = [];
        $("#ruleAddPanel").hidden = true;
        updateRuleEditor();
    }

    function confirmRuleAdd() {
        const judge = currentRuleJudge();
        if (!judge || ruleAddRuleIds.length === 0 || conditionRule(judge)) return;
        const node = ruleAddRuleIds.length === 1
            ? createAtomicRule(ruleAddRuleIds[0])
            : createLogicRule(document.querySelector('input[name="ruleAddLogic"]:checked').value, "", ruleAddRuleIds);
        if (!node) return;
        const blocked = limitBlockReason(judge, node);
        if (blocked) { openConfirm(blocked, { title: "超出编辑器上限", confirmLabel: "知道了" }); return; }
        if (node.type === "B") {
            if (ruleAddArgs[0]) node.args = ruleAddArgs[0];
        } else if (Array.isArray(node.rules)) {
            node.rules.forEach((child, index) => {
                if (child.type === "B" && ruleAddArgs[index]) child.args = ruleAddArgs[index];
            });
        }
        judge.rule = node;
        ruleSelectedId = idOf(node);
        ruleAddRuleIds = [];
        ruleAddArgs = [];
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

    // 判断节点是否处于待配置状态：占位 PH，或动作/条件尚未选择引用规则
    function isUnconfigured(node) {
        if (node.type === "PH") return true;
        if (node.type === "A") {
            const expr = (node.rule?.expr || "").trim();
            return !expr || expr === "∅";
        }
        if (node.type === "C") { const r = conditionRule(node); return !r || (r.type === "B" && !((r.expr || "").trim())); }
        return false;
    }
    function pendingLabel(node) {
        if (node.type === "A") return "待配置动作";
        if (node.type === "C") return "待配置条件";
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
            node.defaultBranch = make.placeholder("default");
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
        if (relation === "next" || relation === "matched") { parent.next = node; return; }
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
        const edge = childEdges(parent).find((candidate) => candidate.node === oldNode && candidate.relation === relation);
        if (!edge) return;
        if (edge.kind === "list") edge.list.splice(edge.index, 1, real);
        else parent[edge.field] = real;
    }

    function relationToSlot(relation) {
        if (relation === "decision") return "decision";
        if (relation === "branch") return "branch";
        if (relation === "default") return "default";
        if (relation === "matched") return "matched";
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
            const j = make.condition(null);
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
        const relation = configTarget?.mode === "add"
            ? configTarget.relation
            : findNode(configTarget?.phId)?.relation;
        populateTypeSelect($("#nodeConfigType"), RELATIONS[relation]?.types || FLOW_TYPES);
        $("#nodeConfigType").value = $("#nodeConfigType").options[0]?.value || "A";
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
        } else if (type === "C") {
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
        } else if (type === "C") {
            const target = configTarget;
            closeNodeConfig();
            const j = make.condition(null);
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

    // 条件配置：把已有占位替换成条件节点（规则暂空=待配置条件），随即打开条件编辑弹窗
    function configureAsCondition(ph) {
        const found = findNode(idOf(ph));
        if (!found || found.node.type !== "PH") return;
        const j = make.condition(null);
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

    function insertNodeBefore(typeOverride = null) {
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !found.parent) return;
        const { node: target, parent, relation } = found;
        if (relation === "decision") return;
        const type = typeOverride || $("#newNodeType").value;
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
        const found = selectedId ? findNode(selectedId) : null;
        if (!found || !canDeleteFlowNode(found.node, found.parent, found.relation)) return;
        const descendants = countFlowNodes(found.node, found.relation !== "next") - 1;
        const nodeName = flowNodeDisplayName(found.node);
        const nodeId = idOf(found.node);
        const message = descendants
            ? `确定删除「${nodeName}」及其 ${descendants} 个流程子节点吗？`
            : `确定删除「${nodeName}」吗？`;
        if (!(await openConfirm(message, { title: "确认删除", confirmLabel: "删除" }))) return;
        const current = findNode(nodeId);
        if (!current || !canDeleteFlowNode(current.node, current.parent, current.relation)) return;
        detachChild(current.parent, current.node, current.relation);
        let selection = current.parent;
        if (["S", "P"].includes(current.parent.type) && current.parent.branches.length === 1) {
            const survivor = current.parent.branches[0];
            const location = findNode(idOf(current.parent));
            if (location?.parent) {
                replaceChild(location.parent, current.parent, location.relation, survivor);
                collapsedIds.delete(idOf(current.parent));
                selection = survivor;
            }
        }
        selectedId = idOf(selection);
        render({ preserveView: true });
        markDraft();
    }

    function toggleCollapse() {
        const found = selectedId ? findNode(selectedId) : null;
        if (!found) return;
        if (!executionEdges(found.node).length || found.node.type === "C") return;
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
        if (executedPaths.size) {
            executedPaths.clear();
            treeRoot.querySelector("#executionTrace")?.remove();
            treeRoot.querySelectorAll(".trace-node").forEach((node) => node.classList.remove("trace-node"));
        }
        $("#testResult").hidden = true;
        dirty = true;
        updateDocStatus();
    }

    function pulseStatus() {
        const pill = $("#docStatus");
        if (pill) { pill.classList.remove("status-pulse"); void pill.offsetWidth; pill.classList.add("status-pulse"); }
    }

    function setSaveBusy(busy) {
        const a = $("#saveDraftButton"), b = $("#promoteButton"), c = $("#testFlowButton");
        const off = busy || flowLoadFailed;
        if (a) a.disabled = off;
        if (b) b.disabled = off;
        if (c) c.disabled = off || !flowMeta;
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
            if (!["R", "B"].includes(node.type)) return;
            collect(node.expr);
            const definition = node.type === "R"
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
        const contextInput = $("#testContextInput");
        if (isEmptyJsonObject(targetInput) && paths.target.length) {
            targetInput.value = formatJson(buildTestJsonSkeleton(paths.target));
        }
        if (isEmptyJsonObject(contextInput) && paths.context.length) {
            contextInput.value = formatJson(buildTestJsonSkeleton(paths.context));
        }
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
            throw new Error(`${label} JSON 格式错误：${error.message}`);
        }
        if (objectOnly && (!value || typeof value !== "object" || Array.isArray(value))) {
            throw new Error(`${label} 必须是 JSON 对象`);
        }
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
        if (placeholderGate() || requiredParamGate()) return;
        materializeParamDefaults(tree);
        let target;
        let context;
        try {
            target = parseTestJson($("#testTargetInput"), "$ 输入数据");
            context = parseTestJson($("#testContextInput"), "$$ 上下文", true);
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
                context
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
                return `<li><span>${escapeText(node ? flowNodeDisplayName(node) : path)}</span><code>${escapeText(path)}</code></li>`;
            }).join("");
        } catch (error) {
            showTestFailure(error.message);
        } finally {
            setTestBusy(false);
        }
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

    function requiredParamGate() {
        const missing = missingRequiredInRule(tree);
        if (!missing.length) return false;
        const names = [...new Set(missing.flatMap((item) => item.params))];
        openConfirm(`请先修正规则参数：${names.join("、")}`,
            { title: "参数不完整或类型不符", confirmLabel: "知道了" });
        return true;
    }

    // 保存到后端：过滤瞬时态后 serialize(tree) → 草稿走 PUT /api/flows/{id}，生效走 POST /publish；
    // 不再由前端传 status（后端按端点决定草稿/生效，PUT 不能发布）。
    async function saveToBackend(makeFormal) {
        if (savingFlow) return true;
        if (placeholderGate() || requiredParamGate()) return false;
        materializeParamDefaults(tree);
        if (!flowMeta || !window.MosikaApi) {
            docStatus = makeFormal ? "formal" : "draft";
            dirty = false;
            updateDocStatus(); pulseStatus();
            return true;
        }
        savingFlow = true; setSaveBusy(true);
        try {
            const payload = {
                flowId: flowMeta.flowId, namespace: flowMeta.namespace,
                name: flowMeta.name, description: flowMeta.description,
                ruleTree: JSON.stringify(T.serialize(tree)), version: flowMeta.version
            };
            const updated = makeFormal
                ? await window.MosikaApi.publishFlow(flowMeta.flowId, payload)
                : await window.MosikaApi.updateFlow(flowMeta.flowId, payload);
            if (updated) { flowMeta.version = updated.version; flowMeta.name = updated.name; flowMeta.description = updated.description; }
            docStatus = makeFormal ? "formal" : "draft";
            dirty = false;
            updateDocStatus(); pulseStatus();
            // 保存/生效成功后自动返回场景列表（dirty 已清空，不再触发离开提醒）。
            setTimeout(() => { location.href = window.MosikaNs ? window.MosikaNs.link("/") : "/"; }, 300);
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
            const hasDefault = !!node.defaultBranch;
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
        if (node?.type === "PH") { openNodeConfig(node); return; }
        if (node?.type === "A" && isUnconfigured(node)) { beginFieldEdit("expression"); return; }
        if (node?.type === "C") openRuleDialog(nodeId);
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
            if (node.type === "C") items.push({ label: "查看规则", icon: "rule", kind: "normal", onClick: () => openRuleDialog(idOf(node)) });
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
        walk(tree, (node) => { if (node.type !== "C" && executionEdges(node).length) setCollapsed(node, true); });
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
    $("#testFlowButton").addEventListener("click", () => setTestPanelOpen(!testPanelOpen));
    $("#testPanelClose").addEventListener("click", () => setTestPanelOpen(false));
    $("#runTestButton").addEventListener("click", runFlowTest);
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
        if (found?.node.type === "C") openRuleDialog(selectedId);
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
    const onFlowArgInput = (event) => {
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
        // 「填了才能保存」：规则子树内任一原子规则的必填模板参数未填写则拦截保存，并定位到该节点。
        const judge = currentRuleJudge();
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
    $("#ruleAddCancelButton").addEventListener("click", cancelRuleAdd);
    $("#ruleAddConfirmButton").addEventListener("click", confirmRuleAdd);
    $("#ruleAddRowButton").addEventListener("click", appendRuleAddRow);
    // 暂存行参数录入：就地写回该行 args 暂存值，不重渲染以保留输入焦点
    const onRuleAddArgInput = (event) => {
        if (!event.target.classList.contains("arg-control")) return false;
        const wrap = event.target.closest("[data-rule-add-params]");
        if (!wrap) return false;
        const index = Number(wrap.dataset.ruleAddParams);
        if (!Number.isInteger(index) || ruleAddRuleIds[index] === undefined) return false;
        const staging = stagingRuleNode(index);
        commitArgsFromInputs(staging, wrap);
        ruleAddArgs[index] = staging.args || "";
        refreshArgMarks(staging, wrap);
        return true;
    };
    $("#ruleAddRows").addEventListener("input", onRuleAddArgInput);
    $("#ruleAddRows").addEventListener("change", (event) => {
        if (onRuleAddArgInput(event)) return;
        const index = Number(event.target.dataset.ruleAddSelect);
        if (!Number.isInteger(index) || ruleAddRuleIds[index] === undefined) return;
        if (ruleAddRuleIds[index] !== event.target.value) ruleAddArgs[index] = "";
        ruleAddRuleIds[index] = event.target.value;
        renderRuleAddRows(index);
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
        const target = node.type === "B" ? node : node.rules[idx];
        if (target && target.type === "B") {
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
                if (found) found.node.rule = ruleDialogSnapshot.rule;
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
        axis: "x",
        branchAttr: "data-branch-id",
        shellIdKey: "nodeId",
        findEntry: (id) => findNode(id),
        relationOf: (entry) => entry.relation,
        labelOf: (node) => {
            const type = TYPES[node.type] || TYPES.A;
            if (node.type === "C") return ruleDisplayName(node);
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
            const type = TYPES[node.type] || TYPES.B;
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

    // 对外桥接层，供页面接线和浏览器交互测试使用。
    window.__mosikaEditor = {
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
        if (!window.MosikaApi) return;
        const flowId = resolveFlowId();
        // 无 flowId = 独立演示，保留内置示例与演示规则定义，不触碰后端。
        if (!flowId) return;
        try {
            const flow = await window.MosikaApi.getFlow(flowId);
            if (!flow) throw new Error("场景不存在");
            flowMeta = {
                flowId: flow.flowId, namespace: flow.namespace,
                name: flow.name, description: flow.description, version: flow.version
            };
            const [atomics, flows] = await Promise.all([
                window.MosikaApi.listRuleReferences(flow.namespace),
                window.MosikaApi.listFlowReferences(flow.namespace)
            ]);
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
                if (backEl) backEl.setAttribute("href", window.MosikaNs.link("/"));
            }
            const titleEl = document.querySelector(".canvas-titlebar strong");
            if (titleEl) titleEl.textContent = flow.name || "流程画布";
            const crumbEl = document.getElementById("flowCrumbName");
            if (crumbEl) crumbEl.textContent = flow.name || "规则树编辑器";
            document.title = `${flow.name || "场景"} · Mosika`;
            loadTree(flow.ruleTree);
            docStatus = flow.status === 1 ? "formal" : flow.status === 2 ? "disabled" : "draft";
            dirty = false;
            updateDocStatus();
            setSaveBusy(false);
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

})();
