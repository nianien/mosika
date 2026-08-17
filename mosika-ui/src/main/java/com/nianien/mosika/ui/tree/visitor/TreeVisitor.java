package com.nianien.mosika.ui.tree.visitor;

import com.nianien.mosika.ui.tree.node.TreeNode;
import com.nianien.mosika.ui.tree.node.define.BranchNode;
import com.nianien.mosika.ui.tree.node.define.FlowNode;
import com.nianien.mosika.ui.tree.node.define.RuleNode;
import com.nianien.mosika.ui.tree.node.define.TypeNode;
import com.nianien.mosika.ui.tree.node.define.UINode;
import com.nianien.mosika.ui.tree.node.flow.ANode;
import com.nianien.mosika.ui.tree.node.flow.CNode;
import com.nianien.mosika.ui.tree.node.flow.DNode;
import com.nianien.mosika.ui.tree.node.flow.PNode;
import com.nianien.mosika.ui.tree.node.flow.SNode;
import com.nianien.mosika.ui.tree.node.rule.HNode;
import com.nianien.mosika.ui.tree.node.rule.LNode;
import com.nianien.mosika.ui.tree.node.rule.RNode;

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
        if (node instanceof RNode rule && !(node instanceof LNode)) {
            collectRuleId(rule, result);
        }
    };

    /** 收集动作节点引用的原子规则表达式 */
    public static final BiConsumer<TypeNode, Set<String>> ACTION_RULE_ID_COLLECTOR = (node, result) -> {
        if (node instanceof ANode action) {
            collectReferences(action.getRule(), result);
        }
    };

    /** 收集条件节点引用的原子规则表达式 */
    public static final BiConsumer<TypeNode, Set<String>> CONDITION_RULE_ID_COLLECTOR = (node, result) -> {
        if (node instanceof CNode condition) {
            collectReferences(condition.getRule(), result);
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
        if (node instanceof SNode serial && serial.getBranches().isEmpty()
                || node instanceof PNode parallel && parallel.getBranches().isEmpty()) {
            throw new IllegalStateException(node.getClass().getSimpleName() + " requires at least one branch");
        }
        if (node instanceof HNode hits) {
            hits.validate();
        } else if (node instanceof LNode logic) {
            if (!"&&".equals(logic.getExpr()) && !"||".equals(logic.getExpr())) {
                throw new IllegalStateException("LNode's expr must be \"&&\" or \"||\": "
                        + logic.getExpr());
            }
            if (logic.getRules().size() < 2) {
                throw new IllegalStateException("LNode requires at least two rules");
            }
        } else if (node instanceof RNode rule
                && (rule.getExpr() == null || rule.getExpr().trim().isEmpty())) {
            throw new IllegalStateException("RNode's expr cannot be empty");
        }
        if (node instanceof DNode decision) {
            if (decision.getBranches().isEmpty()
                    || decision.getBranches().size() == 1 && decision.getDefaultBranch() == null) {
                throw new IllegalStateException("DNode requires at least two outcomes");
            }
            UINode defaultBranch = decision.getDefaultBranch();
            if (defaultBranch != null
                    && !(defaultBranch instanceof ANode)
                    && !(defaultBranch instanceof DNode)
                    && !(defaultBranch instanceof PNode)
                    && !(defaultBranch instanceof SNode)) {
                throw new IllegalStateException("DNode defaultBranch must be ANode, DNode, PNode or SNode");
            }
        }
    };

    private static void collectReferences(RuleNode node, Set<String> result) {
        if (node instanceof LNode logic) {
            for (RNode rule : logic.getRules()) {
                collectReferences(rule, result);
            }
        } else if (node instanceof RNode rule) {
            collectReference(rule, result);
        }
    }

    private static void collectRuleId(RNode node, Set<String> result) {
        String expr = node.getExpr();
        if (expr != null && RULE_ID.matcher(expr).matches()) {
            result.add(expr);
        }
    }

    private static void collectReference(RNode node, Set<String> result) {
        String expr = node.getExpr();
        if (expr != null && !expr.isBlank()) {
            result.add(expr);
        }
    }
}
