package com.skyfalling.mosika.ui.tree.node.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skyfalling.mosika.ui.tree.node.define.IRNode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 与/或逻辑组合规则节点。
 * <p>
 * {@code expr}只能是{@code &&}或{@code ||}，{@code rules}按表达式顺序保存两个或以上
 * 纯规则子树。节点继承的{@code name}表示整个组合规则的可编辑名称，
 * 不会传播或覆盖子规则名称；继承的{@code label}仍只是通用节点展示标签。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * <p>
 * Created on 2022-07-19
 */
public class LNode extends RNode implements IRNode {

    /**
     * 按逻辑表达式顺序保存的直接子规则。
     */
    @Getter
    private List<RNode> rules = new ArrayList<>();

    /**
     * 创建逻辑组合节点。
     *
     * @param expr 逻辑运算符，只能是{@code &&}或{@code ||}
     */
    @JsonCreator(mode = Mode.PROPERTIES)
    public LNode(@JsonProperty("expr") String expr) {
        super(expr);
    }

    /**
     * 将子规则追加到有序规则列表末尾。
     *
     * @param rNode 待追加的纯规则子树
     * @return 当前逻辑组合节点
     */
    public LNode addRule(RNode rNode) {
        this.rules.add(rNode);
        return this;
    }

    /**
     * 创建“与”组合节点。
     *
     * @return 使用{@code &&}运算符的组合节点
     */
    public static LNode and() {
        return new LNode("&&");
    }

    /**
     * 创建“或”组合节点。
     *
     * @return 使用{@code ||}运算符的组合节点
     */
    public static LNode or() {
        return new LNode("||");
    }


    /**
     * 按子规则顺序生成逻辑组合DSL，并应用当前组合节点的取反状态。
     *
     * @return 逻辑组合DSL表达式
     */
    @Override
    public String ruleExpr() {
        String ruleExpr = String.join(getExpr(), this.rules.stream()
                .map(RNode::ruleExpr)
                .collect(Collectors.toList()));
        return (isNegative() ? "!" : "") + (this.rules.size() > 1 ? "(" + ruleExpr + ")" : ruleExpr);
    }
}
