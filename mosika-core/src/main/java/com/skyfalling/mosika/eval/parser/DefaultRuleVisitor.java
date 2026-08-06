package com.skyfalling.mosika.eval.parser;

import com.nianien.antlr4.RuleBaseVisitor;
import com.nianien.antlr4.RuleParser;
import com.skyfalling.mosika.eval.node.CaseNode;
import com.skyfalling.mosika.eval.node.ExprNode;
import com.skyfalling.mosika.eval.node.HitsNode;
import com.skyfalling.mosika.eval.node.ParNode;
import com.skyfalling.mosika.eval.node.RuleNode;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 默认规则计算
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@AllArgsConstructor
public class DefaultRuleVisitor extends RuleBaseVisitor {

    private NodeGenerator generator;

    @Override
    public Object visitPAR(RuleParser.PARContext ctx) {
        RuleNode r1 = (RuleNode) ctx.expr(0).accept(this);
        RuleNode r2 = (RuleNode) ctx.expr(1).accept(this);
        if (r1 instanceof ParNode) {
            return r1.next(r2);
        }
        return new ParNode(r1, r2);
    }

    @Override
    public Object visitSER(RuleParser.SERContext ctx) {
        RuleNode r1 = (RuleNode) ctx.expr(0).accept(this);
        RuleNode r2 = (RuleNode) ctx.expr(1).accept(this);
        return r1.next(r2);
    }

    @Override
    public Object visitOR(RuleParser.ORContext ctx) {
        RuleNode r1 = (RuleNode) ctx.expr(0).accept(this);
        RuleNode r2 = (RuleNode) ctx.expr(1).accept(this);
        return r1.or(r2);
    }


    @Override
    public Object visitAND(RuleParser.ANDContext ctx) {
        RuleNode r1 = (RuleNode) ctx.expr(0).accept(this);
        RuleNode r2 = (RuleNode) ctx.expr(1).accept(this);
        return r1.and(r2);
    }

    @Override
    public Object visitIF(RuleParser.IFContext ctx) {
        RuleNode r1 = (RuleNode) ctx.expr(0).accept(this);
        RuleNode r2 = (RuleNode) ctx.expr(1).accept(this);
        RuleParser.ExprContext expr = ctx.expr(2);
        RuleNode r3 = expr == null ? null : (RuleNode) expr.accept(this);
        return new CaseNode(r1, r2, r3);
    }


    @Override
    public Object visitNOT(RuleParser.NOTContext ctx) {
        RuleNode r1 = (RuleNode) ctx.expr().accept(this);
        return r1.not();
    }


    @Override
    public Object visitHITS(RuleParser.HITSContext ctx) {
        List<RuleNode> nodes = (List<RuleNode>) ctx.arguments().accept(this);
        Integer minHits = parseBound(ctx.bound(0));
        Integer maxHits = parseBound(ctx.bound(1));
        return new HitsNode(minHits, maxHits, nodes);
    }

    private Integer parseBound(RuleParser.BoundContext ctx) {
        return ctx.UNBOUNDED() == null ? Integer.parseInt(ctx.NUMBER().getText()) : null;
    }

    @Override
    public Object visitArguments(RuleParser.ArgumentsContext ctx) {
        return ctx.expr().stream()
                .map(e -> (RuleNode) e.accept(this))
                .collect(Collectors.toList());
    }


    @Override
    public Object visitID(RuleParser.IDContext ctx) {
        String ruleId = ctx.ID() == null ? ctx.NUMBER().getText() : ctx.ID().getText();
        RuleNode ruleNode = generator.apply(ruleId);
        RuleParser.RuleArgumentsContext arguments = ctx.ruleArguments();
        if (arguments == null) {
            return ruleNode;
        }
        if (!(ruleNode instanceof ExprNode exprNode)) {
            throw new IllegalStateException("rule arguments require a named rule node: " + ruleId);
        }
        String rawArguments = arguments.RULE_ARGUMENT().getText();
        return exprNode.withArguments(rawArguments.substring(3, rawArguments.length() - 3));
    }

    @Override
    public Object visitPAREN(RuleParser.PARENContext ctx) {
        return ctx.expr().accept(this);
    }
}
