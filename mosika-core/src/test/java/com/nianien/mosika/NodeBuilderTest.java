package com.nianien.mosika;

import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.eval.node.AllNode;
import com.nianien.mosika.eval.node.AndNode;
import com.nianien.mosika.eval.node.AnyNode;
import com.nianien.mosika.eval.node.CaseNode;
import com.nianien.mosika.eval.node.CompositeNode;
import com.nianien.mosika.eval.node.ExprNode;
import com.nianien.mosika.eval.node.OrNode;
import com.nianien.mosika.eval.node.ParNode;
import com.nianien.mosika.eval.node.RuleNode;
import com.nianien.mosika.eval.node.SerNode;
import com.nianien.mosika.eval.node.SomeNode;
import com.nianien.mosika.eval.parser.NodeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class NodeBuilderTest {

    private final NodeBuilder builder = new NodeBuilder();

    private RuleNode build(String expr) {
        return builder.build(expr);
    }

    private RuleNode build(String expr, String lhs) {
        return new CaseNode(builder.build(expr), builder.build(lhs), null);
    }

    private RuleNode build(String expr, String lhs, String rhs) {
        return new CaseNode(builder.build(expr), builder.build(lhs), builder.build(rhs));
    }


    @ParameterizedTest
    @CsvSource(
            {
                    "!!((a&&b&&c)||(c||d||e)),(a&&b&&c)||(c||d||e)",
                    "c1?101?true:false:null,c1?(101?true:false):null",
                    "c1?!101&&!102?true:false:null,c1?((!101&&!102)?true:false):null",
                    "1?((2||3)&&(4||5)?6:7):(8&&9?10&&11:12),1?(((2||3)&&(4||5))?6:7):((8&&9)?(10&&11):12)",
                    "(!(!(1&&2)||(4&&5))&&((6||7))),!(!(1&&2)||(4&&5))&&(6||7)",
                    "(((1&&2&&3))||((a||b||c))),(1&&2&&3)||(a||b||c)",
                    "a?b:c?d:e,a?b:(c?d:e)",
                    "a?b:c->d,a?b:(c->d)",
                    "a?b,a?b"
            }
    )
    public void testParse(String expr, String expected) {

        RuleNode node = build(expr);
        System.out.println(expr = node.expr());
        assertEquals(expected, expr);
    }

    @Test
    public void testParse() {
        String expr =
                "1025?((1016&&!1017&&!1018&&1019&&1020&&1021&&1022&&1023)?1026:1027):" +
                        "(1029?((!1024&&1016&&!1028&&!1018&&1019&&1020&&1021&&1023)?1026:1027):1027)";
        RuleNode node = build(expr);
        String actual = node.expr();
        assertEquals(expr, actual);
    }


    @Test
    public void testBuild() {
        assertEquals(build("1", "2?a:b", "3?c:d").expr(), "1?(2?a:b):(3?c:d)");
        assertEquals(build("1", "2?a:b").expr(), "1?(2?a:b)");
        System.out.println(build("1?2?a:b:c").expr());
    }


    @Test
    public void testSer() {
        System.out.println(build("∅->(1001?1002)->1004").expr());
        System.out.println(build("f3?t1:f4?t2:t3").expr());

        SerNode serNode = assertInstanceOf(SerNode.class, build("a->b->c"));
        assertEquals(3, serNode.getNodes().size());
        assertEquals("a->b->c", serNode.expr());
    }

    @Test
    public void testParallel() {
        RuleNode node = build("a=>b=>c");
        ParNode parNode = assertInstanceOf(ParNode.class, node);
        assertEquals(3, parNode.getNodes().size());
        assertEquals("a=>b=>c", parNode.expr());

        ParNode nested = assertInstanceOf(ParNode.class, build("a=>(b=>c)"));
        assertEquals(2, nested.getNodes().size());
        assertInstanceOf(ParNode.class, nested.getNodes().get(1));
        assertEquals("a=>(b=>c)", nested.expr());

        SerNode parThenSer = assertInstanceOf(SerNode.class, build("a=>b->c"));
        assertInstanceOf(ParNode.class, parThenSer.getNodes().get(0));
        assertEquals("(a=>b)->c", parThenSer.expr());

        ParNode serThenPar = assertInstanceOf(ParNode.class, build("a->b=>c"));
        assertInstanceOf(SerNode.class, serThenPar.getNodes().get(0));
        assertEquals("(a->b)=>c", serThenPar.expr());
    }

    @Test
    public void testInvalidExpression() {
        assertThrows(IllegalStateException.class, () -> build(""));
        assertThrows(IllegalStateException.class, () -> build("a&&"));
        assertThrows(IllegalStateException.class, () -> build("a b"));
        assertThrows(IllegalStateException.class, () -> build("hits(1,_,a,b)"));
        assertThrows(IllegalStateException.class, () -> build("limit('2','2',a,b)"));
        assertThrows(IllegalStateException.class, () -> build("@a"));
        assertThrows(IllegalStateException.class, () -> build("call(a)"));
    }

    @Test
    public void testLogicalFunctions() {
        AnyNode any = assertInstanceOf(AnyNode.class, build("any(a,b,c)"));
        assertInstanceOf(OrNode.class, any);
        assertEquals(3, any.getNodes().size());
        assertEquals("any(a,b,c)", any.expr());
        assertEquals("any(a,b)", build("any(a,b)").expr());
        AnyNode singleAny = assertInstanceOf(AnyNode.class, build("any(a)"));
        assertEquals(1, singleAny.getNodes().size());
        assertEquals("any(a)", singleAny.expr());

        AllNode all = assertInstanceOf(AllNode.class, build("all(a,b,c)"));
        assertInstanceOf(AndNode.class, all);
        assertEquals(3, all.getNodes().size());
        assertEquals("all(a,b,c)", all.expr());
        assertEquals("all(a,b)", build("all(a,b)").expr());
        AllNode singleAll = assertInstanceOf(AllNode.class, build("all(a)"));
        assertEquals(1, singleAll.getNodes().size());
        assertEquals("all(a)", singleAll.expr());

        assertInstanceOf(OrNode.class, build("a||b"));
        assertInstanceOf(AndNode.class, build("a&&b"));
        assertEquals("a||b||c", build("a||b||c").expr());
        assertEquals("a&&b&&c", build("a&&b&&c").expr());

        SomeNode node = assertInstanceOf(SomeNode.class, build("some(2,_,1,a,3)"));
        assertEquals(2, node.getMinHits());
        assertNull(node.getMaxHits());
        assertEquals(3, node.getNodes().size());
        assertEquals("some(2,_,1,a,3)", node.expr());
        assertEquals(node.expr(), build(node.expr()).expr());
        assertEquals("some(1,_,a,b)", build("some(1,_,a,b)").expr());

        SomeNode unbounded = assertInstanceOf(SomeNode.class, build("some(_,_,a,b)"));
        assertNull(unbounded.getMinHits());
        assertNull(unbounded.getMaxHits());
        SomeNode inverted = assertInstanceOf(SomeNode.class, build("some(3,2,a,b,c)"));
        assertEquals(3, inverted.getMinHits());
        assertEquals(2, inverted.getMaxHits());
        SomeNode oversized = assertInstanceOf(SomeNode.class, build("some(0,4,a,b,c)"));
        assertEquals(0, oversized.getMinHits());
        assertEquals(4, oversized.getMaxHits());

        assertEquals("all(a,any(b,c,d),some(1,2,e,f))",
                build("all(a,any(b,c,d),some(1,2,e,f))").expr());
    }

    @Test
    public void testRuleArguments() {
        String firstExpr = "r1(\"\"\"{\"min\":18,\"text\":\"a && b -> c\"}\"\"\")";
        String secondExpr = "r1(\"\"\"{\"min\":60}\"\"\")";

        ExprNode first = assertInstanceOf(ExprNode.class, build(firstExpr));
        ExprNode second = assertInstanceOf(ExprNode.class, build(secondExpr));

        assertEquals("r1", first.getRuleId());
        assertEquals(Map.of("min", 18, "text", "a && b -> c"), first.getArguments());
        assertEquals(firstExpr, first.expr());
        assertEquals(Map.of("min", 60), second.getArguments());
        assertEquals(secondExpr, second.expr());
        assertNotSame(first, second);
        assertEquals("r1", build("r1").expr());

        RuleNode combined = build(firstExpr + "||" + secondExpr);
        assertEquals(firstExpr + "||" + secondExpr, combined.expr());

        for (String ruleId : List.of("123", "any", "all", "some")) {
            ExprNode rule = assertInstanceOf(ExprNode.class, build(ruleId));
            assertEquals(ruleId, rule.getRuleId());
        }

        ExprNode keywordRule = assertInstanceOf(ExprNode.class, build("any(\"\"\"{}\"\"\")"));
        assertEquals("any", keywordRule.getRuleId());
        assertEquals(Map.of(), keywordRule.getArguments());
    }

    @Test
    public void testMultilineRuleArguments() {
        String arguments = "{\n  \"min\": 18,\n  \"enabled\": true\n}";
        String expression = "r1(\"\"\"" + arguments + "\"\"\")";

        ExprNode node = assertInstanceOf(ExprNode.class, build(expression));
        ExprNode reordered = assertInstanceOf(ExprNode.class,
                build("r1(\"\"\"{\"enabled\":true,\"min\":18}\"\"\")"));

        assertEquals(Map.of("min", 18, "enabled", true), node.getArguments());
        assertEquals("r1(\"\"\"{\"enabled\":true,\"min\":18}\"\"\")", node.expr());
        assertNotSame(node, reordered);
        assertEquals(node.expr(), reordered.expr());
    }

    @Test
    public void testOnlyCompositeRulesAreCached() {
        NodeBuilder compositeBuilder = new NodeBuilder(List.of(
                new RuleDefinition("composite", "a&&b", "", RuleDefinition.RULE_TYPE_COMPOSITE)));

        CompositeNode compiled = assertInstanceOf(CompositeNode.class,
                compositeBuilder.build("composite"));
        assertSame(compiled, compositeBuilder.build("composite"));
        assertNotSame(compositeBuilder.build("a"), compositeBuilder.build("a"));
    }

    @Test
    public void testInvalidRuleArguments() {
        assertThrows(IllegalStateException.class, () -> build("r1()"));
        assertThrows(IllegalStateException.class, () -> build("r1(\"{\\\"min\\\":18}\")"));
        assertThrows(IllegalStateException.class, () -> build("r1(\"\"\"{\"min\":18}\"\")"));
        assertThrows(IllegalArgumentException.class, () -> build("r1(\"\"\"{\"min\":}\"\"\")"));
        assertThrows(IllegalArgumentException.class, () -> build("r1(\"\"\"[1,2]\"\"\")"));
        assertThrows(IllegalArgumentException.class, () -> build("r1(\"\"\"\"\"\")"));
    }

    @Test
    public void parsedNodeMutationDoesNotPolluteLaterBuilds() {
        AndNode parsed = assertInstanceOf(AndNode.class, build("a&&b"));
        RuleNode extended = parsed.and(new ExprNode("c"));

        assertSame(parsed, extended);
        assertEquals("a&&b&&c", extended.expr());
        assertEquals("a&&b&&c", parsed.expr());
        assertEquals("a&&b", build("a&&b").expr());
    }


}
