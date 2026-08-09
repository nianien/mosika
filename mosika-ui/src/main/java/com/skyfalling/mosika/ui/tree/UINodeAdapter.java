package com.skyfalling.mosika.ui.tree;

import com.skyfalling.mosika.eval.node.CaseNode;
import com.skyfalling.mosika.eval.node.ExprNode;
import com.skyfalling.mosika.eval.node.ParNode;
import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.eval.node.SerNode;
import com.skyfalling.mosika.eval.parser.NodeBuilder;
import com.skyfalling.mosika.ui.tree.node.define.UINode;
import com.skyfalling.mosika.ui.tree.node.flow.ANode;
import com.skyfalling.mosika.ui.tree.node.flow.CNode;
import com.skyfalling.mosika.ui.tree.node.flow.DNode;
import com.skyfalling.mosika.ui.tree.node.flow.PNode;
import com.skyfalling.mosika.ui.tree.node.flow.SNode;
import com.skyfalling.mosika.utils.Constants;

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
        if (node instanceof ANode action) {
            return aNode2Rule(action, new ArrayList<>());
        }
        if (node instanceof CNode condition) {
            return cNode2Rule(condition);
        }
        if (node instanceof DNode decision) {
            return dNode2Rule(decision);
        }
        if (node instanceof SNode serial) {
            return sNode2Rule(serial);
        }
        if (node instanceof PNode parallel) {
            return pNode2Rule(parallel);
        }
        throw new UnsupportedOperationException("not support node type:"
                + node.getClass().getSimpleName());
    }

    private RuleNode pNode2Rule(PNode node) {
        List<RuleNode> rules = new ArrayList<>(node.getBranches().size() + 1);
        rules.add(new ExprNode(Constants.NOP));
        for (UINode branch : node.getBranches()) {
            rules.add(toRule(branch));
        }
        return new ParNode(rules.toArray(new RuleNode[0]));
    }

    private RuleNode sNode2Rule(SNode node) {
        List<RuleNode> rules = new ArrayList<>(node.getBranches().size() + 1);
        rules.add(new ExprNode(Constants.NOP));
        for (UINode branch : node.getBranches()) {
            rules.add(toRule(branch));
        }
        return new SerNode(rules.toArray(new RuleNode[0]));
    }

    private RuleNode cNode2Rule(CNode node) {
        RuleNode condition = parse(node.getRule().ruleExpr());
        if (node.getNext() == null) {
            return condition;
        }
        return new CaseNode(condition, toRule(node.getNext()));
    }

    private RuleNode dNode2Rule(DNode node) {
        if (node.getBranches().isEmpty()
                || node.getBranches().size() == 1 && node.getDefaultBranch() == null) {
            throw new IllegalArgumentException("DNode requires at least two outcomes!");
        }
        RuleNode fallback = node.getDefaultBranch() == null
                ? null
                : toRule(node.getDefaultBranch());
        for (int i = node.getBranches().size() - 1; i >= 0; i--) {
            CNode branch = node.getBranches().get(i);
            RuleNode matched = branch.getNext() == null
                    ? new ExprNode(Constants.NOP)
                    : toRule(branch.getNext());
            fallback = new CaseNode(parse(branch.getRule().ruleExpr()), matched, fallback);
        }
        return fallback;
    }

    private RuleNode aNode2Rule(ANode node, List<RuleNode> rules) {
        rules.add(parse(node.getRule().ruleExpr()));
        UINode next = node.getNext();
        if (next != null) {
            if (next instanceof ANode action) {
                return aNode2Rule(action, rules);
            }
            rules.add(toRule(next));
        }
        return rules.size() == 1 ? rules.get(0) : new SerNode(rules.toArray(new RuleNode[0]));
    }

    private RuleNode parse(String expression) {
        return UI_NODE_BUILDER.build(expression);
    }
}
