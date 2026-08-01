package com.skyfalling.mousika.eval.node;

import com.skyfalling.mousika.eval.context.RuleContext;
import com.skyfalling.mousika.eval.result.EvalResult;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 条件与
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class AndNode implements RuleNode {

    private final List<RuleNode> nodes;

    /**
     * 多个节点条件取且
     */
    public AndNode(RuleNode... nodes) {
        this.nodes = List.copyOf(Arrays.asList(nodes));
    }

    @Override
    public RuleNode and(RuleNode node) {
        RuleNode[] combined = Arrays.copyOf(nodes.toArray(new RuleNode[0]), nodes.size() + 1);
        combined[combined.length - 1] = node;
        return new AndNode(combined);
    }

    @Override
    public EvalResult eval(RuleContext context) {
        for (RuleNode node : nodes) {
            if (!context.visit(node).isMatched()) {
                return new EvalResult(expr(), false);
            }
        }
        return new EvalResult(expr(), true);
    }


    @Override
    public String expr() {
        return String.join("&&", nodes.stream()
                .map(Objects::toString)
                .collect(Collectors.toList()));
    }

    @Override
    public String toString() {
        return nodes.size() > 1 ? "(" + expr() + ")" : expr();
    }
}
