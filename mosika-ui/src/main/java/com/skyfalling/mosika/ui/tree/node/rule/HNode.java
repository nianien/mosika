package com.skyfalling.mosika.ui.tree.node.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.stream.Collectors;

/**
 * 命中数量组合规则节点，对应{@code hits(min,max,...)}。
 * <p>
 * 子规则保存在继承的有序{@code rules}列表中；{@code minHits}或{@code maxHits}
 * 为{@code null}时，对应DSL中的无边界符号{@code _}。节点继承的{@code name}
 * 表示整个命中数组合的可编辑名称，继承的{@code label}仍只是通用节点展示标签。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class HNode extends LNode {

    /**
     * 最小命中数；{@code null}表示不设置下界。
     */
    private final Integer minHits;

    /**
     * 最大命中数；{@code null}表示不设置上界。
     */
    private final Integer maxHits;

    /**
     * 创建命中数量组合节点。
     *
     * @param minHits 最小命中数，{@code null}表示无下界
     * @param maxHits 最大命中数，{@code null}表示无上界
     */
    @JsonCreator(mode = Mode.PROPERTIES)
    public HNode(@JsonProperty("minHits") Integer minHits,
                 @JsonProperty("maxHits") Integer maxHits) {
        super("hits");
        this.minHits = minHits;
        this.maxHits = maxHits;
    }

    /**
     * 生成{@code hits}组合DSL，并应用当前节点的取反状态。
     *
     * @return 命中数量组合DSL表达式
     */
    @Override
    public String ruleExpr() {
        String rules = getRules().stream()
                .map(RNode::ruleExpr)
                .collect(Collectors.joining(","));
        String expression = "hits(" + boundExpr(minHits) + "," + boundExpr(maxHits) + "," + rules + ")";
        return (isNegative() ? "!" : "") + expression;
    }

    /**
     * 校验子规则和命中数量边界。
     * <p>
     * 至少需要一个子规则，上下界不能同时为空，且有效区间必须落在子规则数量范围内。
     *
     * @throws IllegalStateException 子规则或边界不合法
     */
    public void validate() {
        if (getRules().isEmpty()) {
            throw new IllegalStateException("hits rules cannot be empty");
        }
        if (minHits == null && maxHits == null) {
            throw new IllegalStateException("minHits and maxHits cannot both be unbounded");
        }
        int min = minHits == null ? 0 : minHits;
        int max = maxHits == null ? getRules().size() : maxHits;
        if (min < 0 || min > max || max > getRules().size()) {
            throw new IllegalStateException("invalid hits bounds: " + min + "," + max);
        }
    }

    private String boundExpr(Integer bound) {
        return bound == null ? "_" : String.valueOf(bound);
    }
}
