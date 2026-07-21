package com.skyfalling.mousika.ui.tree.node;

import com.skyfalling.mousika.eval.node.RuleNode;
import com.skyfalling.mousika.ui.tree.UINodeAdapter;
import com.skyfalling.mousika.ui.tree.node.define.BranchNode;
import com.skyfalling.mousika.ui.tree.node.define.TypeNode;
import com.skyfalling.mousika.ui.tree.node.flow.ANode;
import com.skyfalling.mousika.ui.tree.node.flow.CNode;
import com.skyfalling.mousika.ui.tree.node.flow.DNode;
import com.skyfalling.mousika.ui.tree.node.flow.JNode;
import com.skyfalling.mousika.ui.tree.node.rule.LNode;
import com.skyfalling.mousika.ui.tree.visitor.TreeVisitor;
import com.skyfalling.mousika.utils.Constants;
import com.skyfalling.mousika.utils.JsonUtils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * UI树定义
 * Created on 2023/4/27
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class TreeNode extends ANode {

    public TreeNode() {
        super("");
        this.setNext(new ANode(Constants.NOP));
    }

    private static final UINodeAdapter ADAPTER = new UINodeAdapter();


    public RuleNode toRule() {
        return ADAPTER.toRule(getNext());
    }

    public TreeNode fromRule(RuleNode ruleNode) {
        this.setNext(ADAPTER.fromRule(ruleNode));
        return this;
    }

    public static TreeNode fromJson(String json) {
        return JsonUtils.toBean(json, TreeNode.class);
    }

    public String toJson() {
        return JsonUtils.toJson(this);
    }

    /**
     * 收集树中引用的原子规则ID。
     */
    public Set<String> collect() {
        return visit(TreeVisitor.RULE_ID_COLLECTOR, new LinkedHashSet<>());
    }

    /**
     * 校验节点结构和规则参数。
     */
    public void validate() {
        visit(TreeVisitor.NODE_VALIDATOR, null);
    }

    public <T> T visit(BiConsumer<TypeNode, T> consumer, T result) {
        visit(this, consumer, result);
        return result;
    }

    private static <T> void visit(TypeNode node, BiConsumer<TypeNode, T> consumer, T result) {
        if (node == null) {
            return;
        }
        consumer.accept(node, result);
        if (node instanceof JNode) {
            visit(((JNode) node).getRule(), consumer, result);
        }
        if (node instanceof LNode) {
            ((LNode) node).getRules().forEach(rule -> visit(rule, consumer, result));
        }
        if (node instanceof BranchNode) {
            ((BranchNode<?>) node).getBranches().forEach(branch -> visit(branch, consumer, result));
        }
        if (node instanceof CNode) {
            visit(((CNode) node).getAction(), consumer, result);
        }
        if (node instanceof DNode) {
            visit(((DNode) node).getAction(), consumer, result);
        }
        if (node instanceof ANode) {
            visit(((ANode) node).getNext(), consumer, result);
        }
    }

}
