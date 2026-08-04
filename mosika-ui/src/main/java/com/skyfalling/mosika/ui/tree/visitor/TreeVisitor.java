package com.skyfalling.mosika.ui.tree.visitor;

import com.skyfalling.mosika.ui.tree.node.TreeNode;
import com.skyfalling.mosika.ui.tree.node.define.BranchNode;
import com.skyfalling.mosika.ui.tree.node.define.TypeNode;
import com.skyfalling.mosika.ui.tree.node.flow.ANode;
import com.skyfalling.mosika.ui.tree.node.flow.CNode;
import com.skyfalling.mosika.ui.tree.node.flow.DNode;
import com.skyfalling.mosika.ui.tree.node.flow.JNode;
import com.skyfalling.mosika.ui.tree.node.flow.PNode;
import com.skyfalling.mosika.ui.tree.node.flow.SNode;
import com.skyfalling.mosika.ui.tree.node.rule.HNode;
import com.skyfalling.mosika.ui.tree.node.rule.LNode;
import com.skyfalling.mosika.ui.tree.node.rule.RNode;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/**
 * UI树通用遍历操作。
 */
public final class TreeVisitor {

    private static final Pattern RULE_ID = Pattern.compile("[A-Za-z0-9_]+");

    private TreeVisitor() {
    }

    public static final BiConsumer<TypeNode, Set<String>> RULE_ID_COLLECTOR = (node, result) -> {
        if (isActionReference(node) || isConditionReference(node)) {
            collectRuleId(node, result);
        }
    };

    /** 收集动作节点引用，供持久化层校验规则分类。 */
    public static final BiConsumer<TypeNode, Set<String>> ACTION_RULE_ID_COLLECTOR = (node, result) -> {
        if (isActionReference(node)) {
            collectReference(node, result);
        }
    };

    /** 收集条件/判断节点引用，供持久化层校验规则分类。 */
    public static final BiConsumer<TypeNode, Set<String>> CONDITION_RULE_ID_COLLECTOR = (node, result) -> {
        if (isConditionReference(node)) {
            collectReference(node, result);
        }
    };

    public static final BiConsumer<TypeNode, Object> NODE_VALIDATOR = (node, ignored) -> {
        if (!(node instanceof TreeNode) && (node.getExpr() == null || node.getExpr().trim().isEmpty())) {
            throw new IllegalStateException("node's expr cannot be empty:" + node.getClass().getSimpleName());
        }
        if ((node instanceof SNode || node instanceof PNode)
                && ((BranchNode<?>) node).getBranches().isEmpty()) {
            throw new IllegalStateException(node.getClass().getSimpleName() + " requires at least one branch");
        }
        if (node instanceof HNode hits) {
            hits.validate();
        } else if (node instanceof LNode logic) {
            if (!"&&".equals(node.getExpr()) && !"||".equals(node.getExpr())) {
                throw new IllegalStateException("LNode's expr must be \"&&\" or \"||\": " + node.getExpr());
            }
            if (logic.getRules().size() < 2) {
                throw new IllegalStateException("LNode requires at least two rules");
            }
        }
        if (node instanceof JNode judge && judge.getRule() == null) {
            throw new IllegalStateException("JNode rule is required");
        }
        if (node instanceof DNode) {
            DNode decision = (DNode) node;
            if (decision.getBranches().isEmpty()
                    || decision.getBranches().size() == 1 && decision.getAction() == null) {
                throw new IllegalStateException("DNode requires at least two outcomes");
            }
        }
    };

    private static boolean isActionReference(TypeNode node) {
        return node instanceof ANode && !(node instanceof TreeNode);
    }

    private static boolean isConditionReference(TypeNode node) {
        return node instanceof CNode && !(node instanceof JNode)
                || node instanceof RNode && !(node instanceof LNode);
    }

    private static void collectRuleId(TypeNode node, Set<String> result) {
        String expr = node.getExpr();
        if (expr != null && RULE_ID.matcher(expr).matches()) {
            result.add(expr);
        }
    }

    private static void collectReference(TypeNode node, Set<String> result) {
        String expr = node.getExpr();
        if (expr != null && !expr.isBlank()) {
            result.add(expr);
        }
    }
}
