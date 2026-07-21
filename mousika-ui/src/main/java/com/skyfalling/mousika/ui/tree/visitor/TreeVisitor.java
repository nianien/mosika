package com.skyfalling.mousika.ui.tree.visitor;

import com.skyfalling.mousika.ui.tree.node.TreeNode;
import com.skyfalling.mousika.ui.tree.node.define.TypeNode;
import com.skyfalling.mousika.ui.tree.node.flow.ANode;
import com.skyfalling.mousika.ui.tree.node.flow.CNode;
import com.skyfalling.mousika.ui.tree.node.flow.DNode;
import com.skyfalling.mousika.ui.tree.node.flow.JNode;
import com.skyfalling.mousika.ui.tree.node.rule.HNode;
import com.skyfalling.mousika.ui.tree.node.rule.LNode;
import com.skyfalling.mousika.ui.tree.node.rule.RNode;

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
        boolean atomicRule = node instanceof ANode
                || node instanceof CNode && !(node instanceof JNode)
                || node instanceof RNode && !(node instanceof LNode);
        if (atomicRule && RULE_ID.matcher(node.getExpr()).matches()) {
            result.add(node.getExpr());
        }
    };

    public static final BiConsumer<TypeNode, Object> NODE_VALIDATOR = (node, ignored) -> {
        if (!(node instanceof TreeNode) && (node.getExpr() == null || node.getExpr().trim().isEmpty())) {
            throw new IllegalStateException("node's expr cannot be empty:" + node.getClass().getSimpleName());
        }
        if (node instanceof HNode) {
            ((HNode) node).validate();
        } else if (node instanceof LNode
                && !"&&".equals(node.getExpr())
                && !"||".equals(node.getExpr())) {
            throw new IllegalStateException("LNode's expr must be \"&&\" or \"||\": " + node.getExpr());
        }
        if (node instanceof DNode) {
            DNode decision = (DNode) node;
            if (decision.getBranches().isEmpty()
                    || decision.getBranches().size() == 1 && decision.getAction() == null) {
                throw new IllegalStateException("DNode requires at least two outcomes");
            }
            if (decision.getBranches().stream().anyMatch(branch -> branch.getAction() == null)) {
                throw new IllegalStateException("DNode branch action is required");
            }
        }
    };
}
