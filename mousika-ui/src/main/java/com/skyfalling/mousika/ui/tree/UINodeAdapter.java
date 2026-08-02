package com.skyfalling.mousika.ui.tree;

import com.skyfalling.mousika.eval.node.*;
import com.skyfalling.mousika.eval.parser.NodeBuilder;
import com.skyfalling.mousika.eval.parser.NodeGenerator;
import com.skyfalling.mousika.ui.tree.node.define.FlowNode;
import com.skyfalling.mousika.ui.tree.node.flow.*;
import com.skyfalling.mousika.utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 将UI树单向转换为内核执行树。
 * <p>
 * UI树及其JSON是编辑和持久化的唯一事实来源；转换过程只保留执行语义，
 * 不传递{@code label}、规则{@code name}等UI元数据，也不提供逆向转换。
 *
 * Created on 2023/5/2
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class UINodeAdapter {

    /** UI AST 只保留规则 ID 结构，不展开任何进程级复合规则配置。 */
    private static final NodeGenerator UI_NODE_GENERATOR = NodeGenerator.create();

    /**
     * 将流程节点编译为内核规则节点。
     *
     * @param un UI流程节点
     * @return 仅包含执行语义的内核规则树
     */
    public RuleNode toRule(FlowNode un) {
        Objects.requireNonNull(un, "ui node cannot be null!");
        if (un instanceof ANode) {
            ANode an = (ANode) un;
            return aNode2Rule(an, new ArrayList<>());
        }
        if (un instanceof CNode) {/*include JNode*/
            return cNode2Rule((CNode) un);
        }
        if (un instanceof DNode) {
            return dNode2Rule((DNode) un);
        }
        if (un instanceof SNode) {
            return sNode2Rule((SNode) un);
        }
        if (un instanceof PNode) {
            return pNode2Rule((PNode) un);
        }
        throw new UnsupportedOperationException("not support node type:" + un.getClass().getSimpleName());
    }

    /**
     * PNode转Rule
     *
     * @param pn
     * @return
     */
    private RuleNode pNode2Rule(PNode pn) {
        List<RuleNode> rules = pn.getBranches().stream().map(this::toRule).collect(Collectors.toList());
        rules.add(0, new ExprNode(Constants.NOP));
        return new ParNode(rules.toArray(new RuleNode[0]));
    }

    /**
     * SNode转Rule
     *
     * @param sn
     * @return
     */
    private RuleNode sNode2Rule(SNode sn) {
        List<RuleNode> rules = sn.getBranches().stream().map(this::toRule).collect(Collectors.toList());
        rules.add(0, new ExprNode(Constants.NOP));
        return new SerNode(rules.toArray(new RuleNode[0]));
    }

    /**
     * CNode转Rule
     *
     * @param cn
     * @return
     */
    private RuleNode cNode2Rule(CNode cn) {
        if (cn.getAction() == null) {
            return parse(cn.ruleExpr());
        }
        return new CaseNode(parse(cn.ruleExpr()), toRule(cn.getAction()));
    }


    /**
     * DNode转Rule
     *
     * @param dn
     * @return
     */
    private RuleNode dNode2Rule(DNode dn) {
        if (dn.getBranches().isEmpty()
                || dn.getBranches().size() == 1 && dn.getAction() == null) {
            throw new IllegalArgumentException("DNode requires at least two outcomes!");
        }
        List<CNode> branches = new ArrayList<>(dn.getBranches());
        RuleNode fallback = dn.getAction() == null ? null : toRule(dn.getAction());
        for (int i = branches.size() - 1; i >= 0; i--) {
            CNode branch = branches.get(i);
            // 分支 action 可选：命中纯条件分支时以 NOP 表示“已选择但无后续动作”，
            // 从而停止继续检查后面的互斥分支，并保持 matched=true。
            RuleNode matched = branch.getAction() == null
                    ? new ExprNode(Constants.NOP)
                    : toRule(branch.getAction());
            fallback = new CaseNode(parse(branch.ruleExpr()), matched, fallback);
        }
        return fallback;
    }

    /**
     * ANode转Rule
     *
     * @param an
     * @param nodes
     * @return
     */
    private RuleNode aNode2Rule(ANode an, List<RuleNode> nodes) {
        nodes.add(parse(an.getExpr()));
        FlowNode next = an.getNext();
        if (next != null) {
            if (next instanceof ANode) {
                return aNode2Rule((ANode) next, nodes);
            } else {
                nodes.add(toRule(next));
            }
        }
        return nodes.size() == 1 ? nodes.get(0) : new SerNode(nodes.toArray(new RuleNode[0]));
    }

    private RuleNode parse(String expression) {
        return NodeBuilder.build(expression, UI_NODE_GENERATOR);
    }


}
