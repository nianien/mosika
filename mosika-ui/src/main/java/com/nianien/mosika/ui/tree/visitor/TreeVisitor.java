package com.nianien.mosika.ui.tree.visitor;

import com.nianien.mosika.ui.tree.node.TreeNode;
import com.nianien.mosika.ui.tree.node.define.BranchNode;
import com.nianien.mosika.ui.tree.node.define.FlowNode;
import com.nianien.mosika.ui.tree.node.define.RuleNode;
import com.nianien.mosika.ui.tree.node.define.TypeNode;
import com.nianien.mosika.ui.tree.node.define.UINode;
import com.nianien.mosika.ui.tree.node.flow.ActionNode;
import com.nianien.mosika.ui.tree.node.flow.ConditionNode;
import com.nianien.mosika.ui.tree.node.flow.DecisionNode;
import com.nianien.mosika.ui.tree.node.flow.LoopNode;
import com.nianien.mosika.ui.tree.node.flow.ParallelNode;
import com.nianien.mosika.ui.tree.node.flow.SerialNode;
import com.nianien.mosika.ui.tree.node.rule.MatchNode;
import com.nianien.mosika.ui.tree.node.rule.LogicNode;
import com.nianien.mosika.ui.tree.node.rule.AtomicNode;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * 提供 UITree 遍历期间使用的规则收集器和结构校验器
 */
public final class TreeVisitor {

    private static final Pattern RULE_ID = Pattern.compile("[A-Za-z0-9_]+");

    private TreeVisitor() {
    }

    /** 收集执行树引用的全部原子规则标识 */
    public static final BiConsumer<TypeNode, Set<String>> RULE_ID_COLLECTOR = (node, result) -> {
        if (node instanceof AtomicNode rule && !(node instanceof LogicNode)) {
            collectRuleId(rule, result);
        }
    };

    /** 收集动作节点引用的原子规则表达式 */
    public static final BiConsumer<TypeNode, Set<String>> ACTION_RULE_ID_COLLECTOR = (node, result) -> {
        if (node instanceof ActionNode action) {
            collectReferences(action.getRule(), result);
        }
    };

    /** 收集条件节点引用的原子规则表达式 */
    public static final BiConsumer<TypeNode, Set<String>> CONDITION_RULE_ID_COLLECTOR = (node, result) -> {
        if (node instanceof ConditionNode condition) {
            collectReferences(condition.getRule(), result);
        } else if (node instanceof LoopNode loop) {
            collectReferences(loop.getCondition(), result);
        }
    };

    /** 校验单个节点的结构约束 */
    public static final BiConsumer<TypeNode, Object> NODE_VALIDATOR = (node, ignored) -> {
        if (node instanceof TreeNode tree && tree.getNext() == null) {
            throw new IllegalStateException("TreeNode next is required");
        }
        if (node instanceof FlowNode<?> flow && flow.getRule() == null) {
            throw new IllegalStateException(node.getClass().getSimpleName() + " rule is required");
        }
        if (node instanceof SerialNode serial && serial.getBranches().isEmpty()
                || node instanceof ParallelNode parallel && parallel.getBranches().isEmpty()) {
            throw new IllegalStateException(node.getClass().getSimpleName() + " requires at least one branch");
        }
        if (node instanceof LoopNode loop) {
            if (loop.getMax() < 0) {
                throw new IllegalStateException("LoopNode max cannot be negative");
            }
            if (loop.getCondition() == null) {
                throw new IllegalStateException("LoopNode condition is required");
            }
            if (loop.getAction() == null) {
                throw new IllegalStateException("LoopNode action is required");
            }
        }
        if (node instanceof MatchNode hits) {
            hits.validate();
        } else if (node instanceof LogicNode logic) {
            if (!"&&".equals(logic.getExpr()) && !"||".equals(logic.getExpr())) {
                throw new IllegalStateException("LogicNode's expr must be \"&&\" or \"||\": "
                        + logic.getExpr());
            }
            if (logic.getRules().size() < 2) {
                throw new IllegalStateException("LogicNode requires at least two rules");
            }
        } else if (node instanceof AtomicNode rule
                && (rule.getExpr() == null || rule.getExpr().trim().isEmpty())) {
            throw new IllegalStateException("AtomicNode's expr cannot be empty");
        }
        if (node instanceof DecisionNode decision) {
            if (decision.getBranches().isEmpty()
                    || decision.getBranches().size() == 1 && decision.getDefaultBranch() == null) {
                throw new IllegalStateException("DecisionNode requires at least two outcomes");
            }
            UINode defaultBranch = decision.getDefaultBranch();
            if (defaultBranch != null
                    && !(defaultBranch instanceof ActionNode)
                    && !(defaultBranch instanceof DecisionNode)
                    && !(defaultBranch instanceof LoopNode)
                    && !(defaultBranch instanceof ParallelNode)
                    && !(defaultBranch instanceof SerialNode)) {
                throw new IllegalStateException(
                        "DecisionNode defaultBranch must be ActionNode, DecisionNode, LoopNode, ParallelNode or SerialNode");
            }
        }
    };

    private static void collectReferences(RuleNode node, Set<String> result) {
        if (node instanceof LogicNode logic) {
            for (AtomicNode rule : logic.getRules()) {
                collectReferences(rule, result);
            }
        } else if (node instanceof AtomicNode rule) {
            collectReference(rule, result);
        }
    }

    private static void collectRuleId(AtomicNode node, Set<String> result) {
        String expr = node.getExpr();
        if (expr != null && RULE_ID.matcher(expr).matches()) {
            result.add(expr);
        }
    }

    private static void collectReference(AtomicNode node, Set<String> result) {
        String expr = node.getExpr();
        if (expr != null && !expr.isBlank()) {
            result.add(expr);
        }
    }
}
