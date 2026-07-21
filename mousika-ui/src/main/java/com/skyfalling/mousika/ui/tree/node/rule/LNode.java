package com.skyfalling.mousika.ui.tree.node.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skyfalling.mousika.ui.tree.node.define.IRNode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Logic组合规则节点
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * <p>
 * Created on 2022-07-19
 */
public class LNode extends RNode implements IRNode {

    /**
     * 规则组
     */
    @Getter
    private List<RNode> rules = new ArrayList<>();

    @JsonCreator(mode = Mode.PROPERTIES)
    public LNode(@JsonProperty("expr") String expr) {
        super(expr);
    }

    /**
     * 添加子规则
     *
     * @param rNode
     * @return
     */
    public LNode addRule(RNode rNode) {
        this.rules.add(rNode);
        return this;
    }

    public static LNode and() {
        return new LNode("&&");
    }

    public static LNode or() {
        return new LNode("||");
    }


    /**
     * 规则表达式,默认生成逻辑组合表达式
     */
    @Override
    public String ruleExpr() {
        String ruleExpr = String.join(getExpr(), this.rules.stream()
                .map(RNode::ruleExpr)
                .collect(Collectors.toList()));
        return (isNegative() ? "!" : "") + (this.rules.size() > 1 ? "(" + ruleExpr + ")" : ruleExpr);
    }
}
