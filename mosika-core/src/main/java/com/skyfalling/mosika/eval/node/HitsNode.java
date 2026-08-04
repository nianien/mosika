package com.skyfalling.mosika.eval.node;

import com.skyfalling.mosika.eval.context.RuleContext;
import com.skyfalling.mosika.eval.result.EvalResult;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 命中数量节点，用于判断子规则的命中数是否位于指定区间。
 * null 边界表示不限制。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class HitsNode implements RuleNode {

    private final Integer minHits;
    private final Integer maxHits;
    private final List<RuleNode> nodes;

    public HitsNode(Integer minHits, Integer maxHits, List<RuleNode> nodes) {
        this.minHits = minHits;
        this.maxHits = maxHits;
        this.nodes = List.copyOf(Objects.requireNonNull(nodes, "rules cannot be null"));
        validate();
    }

    @Override
    public EvalResult eval(RuleContext context) {
        int hits = 0;
        for (int i = 0; i < nodes.size(); i++) {
            RuleNode node = nodes.get(i);
            if (context.visit(node).isMatched()) {
                hits++;
            }
            int remaining = nodes.size() - i - 1;
            if (maxHits != null && hits > maxHits) {
                return result(false);
            }
            if (minHits != null && hits + remaining < minHits) {
                return result(false);
            }
            boolean minGuaranteed = minHits == null || hits >= minHits;
            boolean maxGuaranteed = maxHits == null || hits + remaining <= maxHits;
            if (minGuaranteed && maxGuaranteed) {
                return result(true);
            }
        }
        boolean matched = (minHits == null || hits >= minHits)
                && (maxHits == null || hits <= maxHits);
        return result(matched);


    }

    @Override
    public String expr() {
        return "hits(" + boundExpr(minHits) + "," + boundExpr(maxHits) + ","
                + nodes.stream().map(Objects::toString).collect(Collectors.joining(",")) + ")";
    }

    @Override
    public String toString() {
        return expr();
    }

    private void validate() {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("hits rules cannot be empty");
        }
        if (minHits == null && maxHits == null) {
            throw new IllegalArgumentException("minHits and maxHits cannot both be unbounded");
        }
        int min = minHits == null ? 0 : minHits;
        int max = maxHits == null ? nodes.size() : maxHits;
        if (min < 0 || max < 0) {
            throw new IllegalArgumentException("hits bounds cannot be negative");
        }
        if (min > max) {
            throw new IllegalArgumentException("minHits cannot be greater than maxHits");
        }
        if (max > nodes.size()) {
            throw new IllegalArgumentException("maxHits cannot be greater than rule count");
        }
    }

    private String boundExpr(Integer bound) {
        return bound == null ? "_" : String.valueOf(bound);
    }

    private EvalResult result(boolean matched) {
        return new EvalResult(expr(), matched, matched);
    }
}
