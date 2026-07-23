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
 * UI树的序列化根节点。
 * <p>
 * 根节点本身不表示业务动作，继承的{@code next}是整棵流程树的唯一入口。
 * 默认入口为{@link Constants#NOP}动作，便于构造空树并保持JSON结构稳定。
 * Created on 2023/4/27
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class TreeNode extends ANode {

    /**
     * 创建带空操作入口的UI树。
     */
    public TreeNode() {
        super("");
        this.setNext(new ANode(Constants.NOP));
    }

    private static final UINodeAdapter ADAPTER = new UINodeAdapter();

    /**
     * 将当前UI流程树还原为内核规则节点。
     *
     * @return 内核规则树
     */
    public RuleNode toRule() {
        return ADAPTER.toRule(getNext());
    }

    /**
     * 使用内核规则树替换当前流程入口。
     *
     * @param ruleNode 内核规则树
     * @return 当前UI树
     */
    public TreeNode fromRule(RuleNode ruleNode) {
        this.setNext(ADAPTER.fromRule(ruleNode));
        return this;
    }

    /**
     * 从JSON反序列化UI树。
     *
     * @param json UI树JSON
     * @return UI树
     */
    public static TreeNode fromJson(String json) {
        return JsonUtils.toBean(json, TreeNode.class);
    }

    /**
     * 将当前UI树序列化为JSON。
     *
     * @return UI树JSON
     */
    public String toJson() {
        return JsonUtils.toJson(this);
    }

    /**
     * 收集树中引用的原子规则ID。
     *
     * @return 保持首次访问顺序的规则ID集合
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

    /**
     * 深度优先遍历流程树和嵌入的规则子树。
     *
     * @param consumer 节点访问操作
     * @param result   遍历期间共享的结果对象
     * @param <T>      结果类型
     * @return 传入的结果对象
     */
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
