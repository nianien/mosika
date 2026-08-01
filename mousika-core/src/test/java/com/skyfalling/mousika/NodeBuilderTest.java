package com.skyfalling.mousika;

import com.skyfalling.mousika.eval.node.HitsNode;
import com.skyfalling.mousika.eval.node.AndNode;
import com.skyfalling.mousika.eval.node.ExprNode;
import com.skyfalling.mousika.eval.node.RuleNode;
import com.skyfalling.mousika.eval.node.ParNode;
import com.skyfalling.mousika.eval.parser.NodeBuilder;
import com.skyfalling.mousika.exception.RuleParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.skyfalling.mousika.eval.parser.NodeBuilder.build;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class NodeBuilderTest {


    @ParameterizedTest
    @CsvSource(
            {
                    "!!((a&&b&&c)||(c||d||e)),(a&&b&&c)||(c||d||e)",
                    "c1?101?true:false:null,c1?(101?true:false):null",
                    "c1?!101&&!102?true:false:null,c1?((!101&&!102)?true:false):null",
                    "1?((2||3)&&(4||5)?6:7):(8&&9?10&&11:12),1?(((2||3)&&(4||5))?6:7):((8&&9)?(10&&11):12)",
                    "(!(!(1&&2)||(4&&5))&&((6||7))),!(!(1&&2)||(4&&5))&&(6||7)",
                    "(((1&&2&&3))||((a||b||c))),(1&&2&&3)||(a||b||c)"
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
        assertEquals(NodeBuilder.build("1", "2?a:b", "3?c:d").expr(), "1?(2?a:b):(3?c:d)");
        assertEquals(NodeBuilder.build("1", "2?a:b").expr(), "1?(2?a:b)");
        System.out.println(NodeBuilder.build("1?2?a:b:c").expr());
    }


    @Test
    public void testSer() {
        System.out.println(NodeBuilder.build("∅->(1001?1002)->1004").expr());
        System.out.println(NodeBuilder.build("f3?t1:f4?t2:t3").expr());
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
    }

    @Test
    public void testInvalidExpression() {
        assertThrows(RuleParseException.class, () -> build(""));
        assertThrows(RuleParseException.class, () -> build("a&&"));
        assertThrows(RuleParseException.class, () -> build("a b"));
        assertThrows(RuleParseException.class, () -> build("hits(_,_,a,b)"));
        assertThrows(RuleParseException.class, () -> build("hits(3,2,a,b,c)"));
        assertThrows(RuleParseException.class, () -> build("hits(0,4,a,b,c)"));
        assertThrows(RuleParseException.class, () -> build("limit('2','2',a,b)"));
    }

    @Test
    public void testHits() {
        HitsNode node = assertInstanceOf(HitsNode.class, build("hits(2,_,1,a,3)"));
        assertEquals(2, node.getMinHits());
        assertNull(node.getMaxHits());
        assertEquals(3, node.getNodes().size());
        assertEquals("hits(2,_,1,a,3)", node.expr());
        assertEquals(node.expr(), build(node.expr()).expr());
    }

    @Test
    public void parsedNodesCannotPolluteLaterBuilds() {
        AndNode parsed = assertInstanceOf(AndNode.class, build("a&&b"));
        RuleNode extended = parsed.and(new ExprNode("c"));

        assertEquals("a&&b&&c", extended.expr());
        assertEquals("a&&b", parsed.expr());
        assertEquals("a&&b", build("a&&b").expr());
        assertThrows(UnsupportedOperationException.class,
                () -> parsed.getNodes().add(new ExprNode("d")));
    }


}
