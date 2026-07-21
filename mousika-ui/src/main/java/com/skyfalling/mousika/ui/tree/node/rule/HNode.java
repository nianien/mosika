package com.skyfalling.mousika.ui.tree.node.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.stream.Collectors;

/**
 * 命中数量组合节点。null 边界表示不限制。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class HNode extends LNode {

    private final Integer minHits;
    private final Integer maxHits;

    @JsonCreator(mode = Mode.PROPERTIES)
    public HNode(@JsonProperty("minHits") Integer minHits,
                 @JsonProperty("maxHits") Integer maxHits) {
        super("hits");
        this.minHits = minHits;
        this.maxHits = maxHits;
    }

    @Override
    public String ruleExpr() {
        String rules = getRules().stream()
                .map(RNode::ruleExpr)
                .collect(Collectors.joining(","));
        String expression = "hits(" + boundExpr(minHits) + "," + boundExpr(maxHits) + "," + rules + ")";
        return (isNegative() ? "!" : "") + expression;
    }

    /**
     * 校验命中数量边界。
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
