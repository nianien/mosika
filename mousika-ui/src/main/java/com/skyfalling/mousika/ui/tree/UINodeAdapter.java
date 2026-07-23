package com.skyfalling.mousika.ui.tree;

import com.google.common.base.Preconditions;
import com.skyfalling.mousika.eval.node.*;
import com.skyfalling.mousika.eval.parser.NodeBuilder;
import com.skyfalling.mousika.ui.tree.node.define.FlowNode;
import com.skyfalling.mousika.ui.tree.node.flow.*;
import com.skyfalling.mousika.utils.Constants;

import java.util.ArrayList;
import java.util.List;
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


    /**
     * 将流程节点编译为内核规则节点。
     *
     * @param un UI流程节点
     * @return 仅包含执行语义的内核规则树
     */
    public RuleNode toRule(FlowNode un) {
        Preconditions.checkNotNull(un, "ui node cannot be null!");
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
            return NodeBuilder.build(cn.ruleExpr());
        }
        return new CaseNode(NodeBuilder.build(cn.ruleExpr()), toRule(cn.getAction()));
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
        CaseNode root = new CaseNode(new ExprNode(""), new ExprNode(""));
        CaseNode current = root;
        for (CNode branch : branches) {
            if (branch.getAction() == null) {
                throw new IllegalArgumentException("DNode branch action is required!");
            }
            CaseNode caseNode = new CaseNode(NodeBuilder.build(branch.ruleExpr()), toRule(branch.getAction()));
            current.setFalseCase(caseNode);
            current = caseNode;
        }
        if (dn.getAction() != null) {
            current.setFalseCase(toRule(dn.getAction()));
        }
        return root.getFalseCase();
    }

    /**
     * ANode转Rule
     *
     * @param an
     * @param nodes
     * @return
     */
    private RuleNode aNode2Rule(ANode an, List<RuleNode> nodes) {
        nodes.add(NodeBuilder.build(an.getExpr()));
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


}
