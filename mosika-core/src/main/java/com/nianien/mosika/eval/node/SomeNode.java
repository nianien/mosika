package com.nianien.mosika.eval.node;

import com.nianien.mosika.eval.context.RuleContext;
import com.nianien.mosika.eval.result.EvalResult;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 命中数量节点，用于判断子规则的命中数是否位于指定区间
 * null 边界表示不限制
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class SomeNode implements RuleNode {

    @Getter
    private final Integer minHits;
    @Getter
    private final Integer maxHits;
    @Getter
    private final List<RuleNode> nodes = new ArrayList<>();
    private final int min;
    private final int max;
    private String expression;

    public SomeNode(Integer minHits, Integer maxHits, RuleNode... nodes) {
        this.minHits = minHits;
        this.maxHits = maxHits;
        this.min = minHits == null ? 0 : minHits;
        this.max = maxHits == null ? nodes.length : maxHits;
        this.nodes.addAll(Arrays.asList(nodes));
    }

    @Override
    public EvalResult eval(RuleContext context) {
        if (min > max || min > nodes.size()) {
            return result(false);
        }
        if (min == 0 && max >= nodes.size()) {
            return result(true);
        }
        int hits = 0;
        for (int i = 0; i < nodes.size(); i++) {
            if (context.visit(nodes.get(i)).isMatched()) {
                hits++;
            }
            int remaining = nodes.size() - i - 1;
            if (hits > max) return result(false);
            if (hits + remaining < min) return result(false);
            if (hits >= min && hits + remaining <= max) return result(true);
        }
        return result(true);
    }

    @Override
    public String expr() {
        if (expression == null) {
            expression = "some(" + boundExpr(minHits) + "," + boundExpr(maxHits) + ","
                    + nodes.stream().map(Objects::toString).collect(Collectors.joining(",")) + ")";
        }
        return expression;
    }

    @Override
    public String toString() {
        return expr();
    }


    private String boundExpr(Integer bound) {
        return bound == null ? "_" : String.valueOf(bound);
    }

    private EvalResult result(boolean matched) {
        return new EvalResult(expr(), matched);
    }
}
