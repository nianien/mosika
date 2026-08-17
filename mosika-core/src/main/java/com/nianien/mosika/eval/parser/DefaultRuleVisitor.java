package com.nianien.mosika.eval.parser;

import com.nianien.antlr4.RuleBaseVisitor;
import com.nianien.antlr4.RuleParser;
import com.nianien.mosika.eval.node.*;
import lombok.AllArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

/**
 * 默认规则计算
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@AllArgsConstructor
public class DefaultRuleVisitor extends RuleBaseVisitor<RuleNode> {
    private static final String QUOTE = "\"\"\"";

    /**
     * 节点生成器
     */
    private final NodeGenerator generator;

    @Override
    public RuleNode visitSEQ(RuleParser.SEQContext ctx) {
        RuleNode r1 = ctx.expr(0).accept(this);
        RuleNode r2 = ctx.expr(1).accept(this);
        if (ctx.op.getType() == RuleParser.SER_OP) {
            return r1 instanceof SerNode ? r1.next(r2) : new SerNode(r1, r2);
        }
        return r1 instanceof ParNode ? r1.next(r2) : new ParNode(r1, r2);
    }

    @Override
    public RuleNode visitOR(RuleParser.ORContext ctx) {
        RuleNode r1 = ctx.expr(0).accept(this);
        RuleNode r2 = ctx.expr(1).accept(this);
        return r1.or(r2);
    }


    @Override
    public RuleNode visitAND(RuleParser.ANDContext ctx) {
        RuleNode r1 = ctx.expr(0).accept(this);
        RuleNode r2 = ctx.expr(1).accept(this);
        return r1.and(r2);
    }

    @Override
    public RuleNode visitIF(RuleParser.IFContext ctx) {
        RuleNode r1 = ctx.expr(0).accept(this);
        RuleNode r2 = ctx.expr(1).accept(this);
        RuleParser.ExprContext expr = ctx.expr(2);
        RuleNode r3 = expr == null ? null : expr.accept(this);
        return new CaseNode(r1, r2, r3);
    }


    @Override
    public RuleNode visitNOT(RuleParser.NOTContext ctx) {
        RuleNode r1 = ctx.expr().accept(this);
        return r1.not();
    }


    @Override
    public RuleNode visitANY(RuleParser.ANYContext ctx) {
        List<RuleNode> nodes = visitExpressions(ctx.arguments().expr());
        return new AnyNode(nodes.toArray(RuleNode[]::new));
    }

    @Override
    public RuleNode visitALL(RuleParser.ALLContext ctx) {
        List<RuleNode> nodes = visitExpressions(ctx.arguments().expr());
        return new AllNode(nodes.toArray(RuleNode[]::new));
    }

    @Override
    public RuleNode visitSOME(RuleParser.SOMEContext ctx) {
        List<RuleNode> nodes = visitExpressions(ctx.arguments().expr());
        Integer minHits = parseBound(ctx.bound(0));
        Integer maxHits = parseBound(ctx.bound(1));
        return new SomeNode(minHits, maxHits, nodes.toArray(RuleNode[]::new));
    }

    private Integer parseBound(RuleParser.BoundContext ctx) {
        return ctx.NUMBER() == null ? null : Integer.parseInt(ctx.NUMBER().getText());
    }

    private List<RuleNode> visitExpressions(List<RuleParser.ExprContext> expressions) {
        return expressions.stream()
                .map(this::visit)
                .toList();
    }

    @Override
    public RuleNode visitPAREN(RuleParser.PARENContext ctx) {
        return ctx.expr().accept(this);
    }


    @Override
    public RuleNode visitID(RuleParser.IDContext ctx) {
        RuleParser.RuleArgumentsContext arguments = ctx.ruleArguments();
        String rawArguments = arguments == null ? null
                : trimQuotes(arguments.RULE_ARGUMENT().getText());
        String ruleId = ctx.getStart().getText();
        return generator.apply(ruleId, rawArguments);
    }


    private static String trimQuotes(String text) {
        return text.substring(QUOTE.length(), text.length() - QUOTE.length());
    }
}
