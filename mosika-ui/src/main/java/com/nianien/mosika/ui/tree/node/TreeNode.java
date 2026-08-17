package com.nianien.mosika.ui.tree.node;

import com.nianien.mosika.eval.node.RuleNode;
import com.nianien.mosika.ui.tree.UINodeAdapter;
import com.nianien.mosika.ui.tree.node.define.BranchNode;
import com.nianien.mosika.ui.tree.node.define.FlowNode;
import com.nianien.mosika.ui.tree.node.define.NameNode;
import com.nianien.mosika.ui.tree.node.define.TypeNode;
import com.nianien.mosika.ui.tree.node.define.UINode;
import com.nianien.mosika.ui.tree.node.flow.ANode;
import com.nianien.mosika.ui.tree.node.flow.DNode;
import com.nianien.mosika.ui.tree.node.rule.LNode;
import com.nianien.mosika.ui.tree.node.rule.RNode;
import com.nianien.mosika.ui.tree.visitor.TreeVisitor;
import com.nianien.mosika.utils.Constants;
import com.nianien.mosika.utils.JsonUtils;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * UITree 的序列化、遍历、校验和编译入口
 * <p>
 * 根节点不表示业务动作或条件，只通过 {@code next} 指向执行树入口
 * <pre>
 *     tN
 *      |
 *     uN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class TreeNode extends NameNode {

    private static final UINodeAdapter ADAPTER = new UINodeAdapter();

    private UINode next;

    /**
     * 创建带空操作入口的 UITree
     */
    public TreeNode() {
        ANode action = new ANode();
        action.setRule(new RNode(Constants.NOP));
        next = action;
    }

    /**
     * 返回执行树入口
     *
     * @return 执行树入口
     */
    public UINode getNext() {
        return next;
    }

    /**
     * 设置执行树入口
     *
     * @param next 执行树入口
     */
    public void setNext(UINode next) {
        this.next = next;
    }

    /**
     * 将当前 UITree 编译为内核执行树
     *
     * @return 内核执行树
     */
    public RuleNode toRule() {
        return ADAPTER.toRule(next);
    }

    /**
     * 从 JSON 反序列化 UITree
     *
     * @param json UITree 的 JSON 文本
     * @return 反序列化后的 UITree
     */
    public static TreeNode fromJson(String json) {
        return JsonUtils.toBean(json, TreeNode.class);
    }

    /**
     * 将当前 UITree 序列化为 JSON
     *
     * @return UITree 的 JSON 文本
     */
    public String toJson() {
        return JsonUtils.toJson(this);
    }

    /**
     * 收集树中引用的原子规则标识
     *
     * @return 按首次访问顺序排列的规则标识集合
     */
    public Set<String> collect() {
        return visit(TreeVisitor.RULE_ID_COLLECTOR, new LinkedHashSet<>());
    }

    /**
     * 校验 UITree 的节点结构和规则参数
     */
    public void validate() {
        visit(TreeVisitor.NODE_VALIDATOR, null);
    }

    /**
     * 校验 UITree 的最大深度、节点数量和引用唯一性
     *
     * @param maxDepth 允许的最大深度，根节点深度为 {@code 1}
     * @param maxNodes 允许的最大节点数量
     * @throws IllegalArgumentException 限制值不是正数
     * @throws IllegalStateException 树超出限制或包含重复引用及循环引用
     */
    public void validateSize(int maxDepth, int maxNodes) {
        if (maxDepth < 1 || maxNodes < 1) {
            throw new IllegalArgumentException("tree limits must be positive");
        }
        Set<TypeNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<VisitFrame> stack = new ArrayDeque<>();
        stack.push(new VisitFrame(this, 1));
        int nodes = 0;
        while (!stack.isEmpty()) {
            VisitFrame frame = stack.pop();
            TypeNode node = frame.node();
            if (!visited.add(node)) {
                throw new IllegalStateException("tree contains a repeated or cyclic node: "
                        + node.getClass().getSimpleName());
            }
            nodes++;
            if (nodes > maxNodes) {
                throw new IllegalStateException("tree node count exceeds limit " + maxNodes);
            }
            if (frame.depth() > maxDepth) {
                throw new IllegalStateException("tree depth exceeds limit " + maxDepth);
            }
            pushChildren(stack, node, frame.depth() + 1);
        }
    }

    private static void pushChildren(ArrayDeque<VisitFrame> stack, TypeNode node, int childDepth) {
        if (node instanceof TreeNode tree) {
            if (tree.getNext() != null) {
                stack.push(new VisitFrame(tree.getNext(), childDepth));
            }
        } else if (node instanceof FlowNode<?> flow) {
            if (flow.getNext() != null) {
                stack.push(new VisitFrame(flow.getNext(), childDepth));
            }
            if (flow.getRule() != null) {
                stack.push(new VisitFrame(flow.getRule(), childDepth));
            }
        } else if (node instanceof DNode decision) {
            if (decision.getDefaultBranch() != null) {
                stack.push(new VisitFrame(decision.getDefaultBranch(), childDepth));
            }
            pushChildren(stack, decision.getBranches(), childDepth);
        } else if (node instanceof BranchNode<?> branch) {
            pushChildren(stack, branch.getBranches(), childDepth);
        } else if (node instanceof LNode logic) {
            pushChildren(stack, logic.getRules(), childDepth);
        }
    }

    private static void pushChildren(ArrayDeque<VisitFrame> stack,
                                     List<? extends TypeNode> children,
                                     int childDepth) {
        for (int i = children.size() - 1; i >= 0; i--) {
            TypeNode child = children.get(i);
            if (child != null) {
                stack.push(new VisitFrame(child, childDepth));
            }
        }
    }

    private record VisitFrame(TypeNode node, int depth) {
    }

    /**
     * 按深度优先顺序访问执行树及其绑定的规则树
     *
     * @param consumer 节点访问操作
     * @param result 遍历期间共享的结果对象
     * @param <T> 结果类型
     * @return 调用方传入的结果对象
     */
    public <T> T visit(BiConsumer<TypeNode, T> consumer, T result) {
        visitNode(this, consumer, result);
        return result;
    }

    private static <T> void visitNode(TypeNode node, BiConsumer<TypeNode, T> consumer, T result) {
        if (node == null) {
            return;
        }
        consumer.accept(node, result);
        if (node instanceof TreeNode tree) {
            visitNode(tree.getNext(), consumer, result);
        } else if (node instanceof FlowNode<?> flow) {
            visitNode(flow.getRule(), consumer, result);
            visitNode(flow.getNext(), consumer, result);
        } else if (node instanceof DNode decision) {
            for (TypeNode child : decision.getBranches()) {
                visitNode(child, consumer, result);
            }
            visitNode(decision.getDefaultBranch(), consumer, result);
        } else if (node instanceof BranchNode<?> branch) {
            for (TypeNode child : branch.getBranches()) {
                visitNode(child, consumer, result);
            }
        } else if (node instanceof LNode logic) {
            for (RNode rule : logic.getRules()) {
                visitNode(rule, consumer, result);
            }
        }
    }
}
