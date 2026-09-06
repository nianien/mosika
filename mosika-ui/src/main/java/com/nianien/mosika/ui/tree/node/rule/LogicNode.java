package com.nianien.mosika.ui.tree.node.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 与或逻辑组合规则节点
 * <p>
 * {@code expr} 只能是 {@code &&} 或 {@code ||}
 * {@code rules} 按表达式顺序保存两个或以上规则子树
 * <pre>
 *       lN
 *      / | \
 *     /  |  \
 *   rN  rN  rN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * <p>
 * Created on 2022-07-19
 */
@Data
public class LogicNode extends BooleanNode {

    /** 按逻辑表达式顺序保存的直接子规则 */
    private final List<AtomicNode> rules = new ArrayList<>();


    /**
     * 创建逻辑组合节点
     *
     * @param expr 逻辑运算符，只能是{@code &&}或{@code ||}
     */
    @JsonCreator(mode = Mode.PROPERTIES)
    public LogicNode(@JsonProperty("expr") String expr) {
        super(expr);
    }

    /**
     * 将子规则追加到有序规则列表末尾
     *
     * @param rule 待追加的规则子树
     * @return 当前逻辑组合节点
     */
    public LogicNode addRule(AtomicNode rule) {
        rules.add(rule);
        return this;
    }

    /**
     * 创建逻辑与组合节点
     *
     * @return 使用 {@code &&} 运算符的逻辑组合节点
     */
    public static LogicNode and() {
        return new LogicNode("&&");
    }

    /**
     * 创建逻辑或组合节点
     *
     * @return 使用 {@code ||} 运算符的逻辑组合节点
     */
    public static LogicNode or() {
        return new LogicNode("||");
    }


    /**
     * 按子规则顺序生成逻辑组合 DSL，并应用当前节点的取反状态
     *
     * @return 逻辑组合 DSL 表达式
     */
    @Override
    public String ruleExpr() {
        String ruleExpr = String.join(getExpr(), this.rules.stream()
                .map(AtomicNode::ruleExpr)
                .collect(Collectors.toList()));
        return (isNegative() ? "!" : "") + (this.rules.size() > 1 ? "(" + ruleExpr + ")" : ruleExpr);
    }
}
