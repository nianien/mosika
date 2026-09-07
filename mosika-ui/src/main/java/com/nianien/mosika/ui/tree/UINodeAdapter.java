package com.nianien.mosika.ui.tree;

import com.nianien.mosika.eval.node.CaseNode;
import com.nianien.mosika.eval.node.ExprNode;
import com.nianien.mosika.eval.node.ParNode;
import com.nianien.mosika.eval.node.RuleNode;
import com.nianien.mosika.eval.node.SerNode;
import com.nianien.mosika.eval.parser.NodeBuilder;
import com.nianien.mosika.ui.tree.node.define.UINode;
import com.nianien.mosika.ui.tree.node.flow.ActionNode;
import com.nianien.mosika.ui.tree.node.flow.ConditionNode;
import com.nianien.mosika.ui.tree.node.flow.DecisionNode;
import com.nianien.mosika.ui.tree.node.flow.LoopNode;
import com.nianien.mosika.ui.tree.node.flow.ParallelNode;
import com.nianien.mosika.ui.tree.node.flow.SerialNode;
import com.nianien.mosika.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 UITree 中的可视化执行节点单向编译为内核执行树
 * <p>
 * 编译过程只读取执行结构和规则表达式，不修改 UITree，也不保留节点名称等展示信息
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class UINodeAdapter {

    private static final NodeBuilder UI_NODE_BUILDER = new NodeBuilder();

    /**
     * 编译指定的可视化执行节点
     *
     * @param node 待编译的执行节点
     * @return 对应的内核执行树
     * @throws UnsupportedOperationException 节点类型不受支持
     */
    public RuleNode toRule(UINode node) {
        if (node instanceof ActionNode action) {
            return actionNode2Rule(action, new ArrayList<>());
        }
        if (node instanceof ConditionNode condition) {
            return conditionNode2Rule(condition);
        }
        if (node instanceof DecisionNode decision) {
            return decisionNode2Rule(decision);
        }
        if (node instanceof SerialNode serial) {
            return serialNode2Rule(serial);
        }
        if (node instanceof ParallelNode parallel) {
            return parallelNode2Rule(parallel);
        }
        if (node instanceof LoopNode loop) {
            return loopNode2Rule(loop);
        }
        throw new UnsupportedOperationException("not support node type:"
                + node.getClass().getSimpleName());
    }

    private RuleNode parallelNode2Rule(ParallelNode node) {
        List<RuleNode> rules = new ArrayList<>(node.getBranches().size() + 1);
        rules.add(new ExprNode(Constants.NOP));
        for (UINode branch : node.getBranches()) {
            rules.add(toRule(branch));
        }
        return new ParNode(rules.toArray(new RuleNode[0]));
    }

    private RuleNode serialNode2Rule(SerialNode node) {
        List<RuleNode> rules = new ArrayList<>(node.getBranches().size() + 1);
        rules.add(new ExprNode(Constants.NOP));
        for (UINode branch : node.getBranches()) {
            rules.add(toRule(branch));
        }
        return new SerNode(rules.toArray(new RuleNode[0]));
    }

    private RuleNode loopNode2Rule(LoopNode node) {
        return new com.nianien.mosika.eval.node.LoopNode(
                node.getMax(), parse(node.getRule().ruleExpr()), toRule(node.getNext()));
    }

    private RuleNode conditionNode2Rule(ConditionNode node) {
        RuleNode condition = parse(node.getRule().ruleExpr());
        if (node.getNext() == null) {
            return condition;
        }
        return new CaseNode(condition, toRule(node.getNext()));
    }

    private RuleNode decisionNode2Rule(DecisionNode node) {
        if (node.getBranches().isEmpty()
                || node.getBranches().size() == 1 && node.getDefaultBranch() == null) {
            throw new IllegalArgumentException("DecisionNode requires at least two outcomes!");
        }
        RuleNode fallback = node.getDefaultBranch() == null
                ? null
                : toRule(node.getDefaultBranch());
        for (int i = node.getBranches().size() - 1; i >= 0; i--) {
            ConditionNode branch = node.getBranches().get(i);
            RuleNode matched = branch.getNext() == null
                    ? new ExprNode(Constants.NOP)
                    : toRule(branch.getNext());
            fallback = new CaseNode(parse(branch.getRule().ruleExpr()), matched, fallback);
        }
        return fallback;
    }

    private RuleNode actionNode2Rule(ActionNode node, List<RuleNode> rules) {
        rules.add(parse(node.getRule().ruleExpr()));
        UINode next = node.getNext();
        if (next != null) {
            if (next instanceof ActionNode action) {
                return actionNode2Rule(action, rules);
            }
            rules.add(toRule(next));
        }
        return rules.size() == 1 ? rules.get(0) : new SerNode(rules.toArray(new RuleNode[0]));
    }

    private RuleNode parse(String expression) {
        return UI_NODE_BUILDER.build(expression);
    }
}
