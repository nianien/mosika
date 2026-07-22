package com.skyfalling.mousika;

import com.skyfalling.mousika.bean.User;
import com.skyfalling.mousika.engine.RuleDefinition;
import com.skyfalling.mousika.engine.RuleEngine;
import com.skyfalling.mousika.engine.UdfDefinition;
import com.skyfalling.mousika.eval.RuleVisitor;
import com.skyfalling.mousika.eval.context.RuleContext;
import com.skyfalling.mousika.eval.node.ExprNode;
import com.skyfalling.mousika.eval.node.ParNode;
import com.skyfalling.mousika.eval.node.RuleNode;
import com.skyfalling.mousika.eval.node.SerNode;
import com.skyfalling.mousika.eval.parser.NodeBuilder;
import com.skyfalling.mousika.eval.result.EvalResult;
import com.skyfalling.mousika.eval.result.NodeResult;
import com.skyfalling.mousika.suite.RuleEvaluator;
import com.skyfalling.mousika.udf.SayHelloUdf;
import com.skyfalling.mousika.utils.Constants;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created on 2023/3/28
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class RuleNodeFlowTest {


    private RuleEngine ruleEngine;

    {
        List<RuleDefinition> ruleDefinitions = Arrays.asList(
                new RuleDefinition("t1", "true", "为真规则1"),
                new RuleDefinition("t2", "true", "为真规则2"),
                new RuleDefinition("t3", "true", "为真规则3"),
                new RuleDefinition("t4", "true", "为真规则4"),
                new RuleDefinition("f1", "false", "为假规则1"),
                new RuleDefinition("f2", "false", "为假规则2"),
                new RuleDefinition("f3", "false", "为假规则3"),
                new RuleDefinition("f4", "false", "为假规则4"),
                new RuleDefinition("a1", "'a1'", "业务操作1"),
                new RuleDefinition("a2", "'a2'", "业务操作2"),
                new RuleDefinition("a3", "'a3'", "业务操作3"),
                new RuleDefinition("a4", "'a4'", "业务操作4")
        );
        ruleEngine = RuleEngine.builder().ruleDefinitions(ruleDefinitions).build();
    }

    @Test
    public void testNop() {
        User root = new User("jack", 19);
        RuleNode node = NodeBuilder.build("(∅->a1)->(∅->a2->a3)");
        System.out.println(node.expr());
        NodeResult nodeResult = new RuleEvaluator(ruleEngine).eval(node, root);
        System.out.println(nodeResult);
    }


    @ParameterizedTest
    @CsvSource(value = {
            "t1?f3?a1:f4?a2:a3#t1?(f3?a1:(f4?a2:a3))",
            "t1?f3?a1:(f4?a2):a3#t1?(f3?a1:(f4?a2)):a3",
            "f1?t2?t3?a1:a2:a3#f1?(t2?(t3?a1:a2):a3)"
    }, delimiter = '#')
    public void testEval(String expr, String expected) {
        User root = new User("jack", 19);
        RuleNode node = NodeBuilder.build(expr);
        System.out.println(node.expr());
        NodeResult nodeResult = new RuleEvaluator(ruleEngine).eval(node, root);
        System.out.println(nodeResult);
        assertEquals(expected, node.expr());

    }

    @Test
    public void testIfNode() {
        User root = new User("jack", 19);
        RuleNode node = NodeBuilder.build("(t1&&t4?t2:t3)?(a1->a2->t1?a3):(a3->a4)");
        System.out.println(node.toString());
        NodeResult nodeResult = new RuleEvaluator(ruleEngine).eval(node, root);
        System.out.println(nodeResult);

    }


    @Test
    public void testSerNodeExecutesAllNodesWithoutBusinessResult() {
        User root = new User("jack", 19);
        RuleNode node = NodeBuilder.build("a1->a2->a3->a4");
        RuleVisitor context = new RuleVisitor(ruleEngine, root);

        EvalResult result = context.visit(node);

        assertTrue(result.isMatched());
        assertNull(result.getResult());
        assertEquals(4, context.getRuleResults().get(0).getSubRules().size());
    }

    @Test
    public void testSerNodeContinuesAfterUnmatchedNode() {
        User root = new User("jack", 19);
        RuleNode node = NodeBuilder.build("a1->f1->a2");
        RuleVisitor context = new RuleVisitor(ruleEngine, root);

        EvalResult result = context.visit(node);

        assertTrue(result.isMatched());
        assertNull(result.getResult());
        assertEquals(3, context.getRuleResults().get(0).getSubRules().size());
        assertEquals("f1", context.getRuleResults().get(0).getSubRules().get(1).getExpr());
        assertEquals("a2", context.getRuleResults().get(0).getSubRules().get(2).getExpr());
    }

    @Test
    public void testSerNodeWithOnlyNopIsMatched() {
        RuleVisitor context = new RuleVisitor(ruleEngine, null);

        EvalResult result = context.visit(new SerNode(new ExprNode(Constants.NOP)));

        assertTrue(result.isMatched());
        assertNull(result.getResult());
    }

    @SneakyThrows
    @Test
    public void tesParNode() {
        User root = new User("jack", 19);
        RuleNode node = NodeBuilder.build("f1=>a1=>a2");
        assertInstanceOf(ParNode.class, node);
        NodeResult nodeResult = new RuleEvaluator(ruleEngine).eval(node, root);
        assertNull(nodeResult.getResult());
        assertEquals(3, nodeResult.getDetails().get(0).getSubRules().size());
    }

    @Test
    public void testParNodeIgnoresBranchMatchResults() {
        RuleVisitor context = new RuleVisitor(ruleEngine, null);
        RuleNode node = NodeBuilder.build("f1=>f2");

        EvalResult result = context.visit(node);

        assertTrue(result.isMatched());
        assertNull(result.getResult());
        assertEquals(2, context.getRuleResults().get(0).getSubRules().size());
    }

    @Test
    public void testParallelContextWrites() {
        for (int attempt = 0; attempt < 25; attempt++) {
            RuleVisitor context = new RuleVisitor(ruleEngine, null);
            RuleNode[] branches = IntStream.range(0, 32)
                    .mapToObj(branch -> contextWriter(branch, 200))
                    .toArray(RuleNode[]::new);

            context.visit(new ParNode(branches));

            assertEquals(6_400, context.size());
            assertEquals(199, context.getProperty("31-199"));
        }
    }

    @Test
    public void testContextSupportsNullValues() {
        RuleVisitor ruleContext = new RuleVisitor(ruleEngine, null);
        assertInstanceOf(LinkedHashMap.class, ruleContext);
        ruleContext.setProperty("nullable", null);
        assertTrue(ruleContext.containsKey("nullable"));
        assertNull(ruleContext.getProperty("nullable"));

        RuleEvaluator evaluator = new RuleEvaluator(ruleEngine);
        Map<String, Object> context = new HashMap<>();
        context.put("nullable", null);

        evaluator.eval(new ExprNode("t1"), null, context);

        assertTrue(context.containsKey("nullable"));
        assertNull(context.get("nullable"));
    }

    private RuleNode contextWriter(int branch, int properties) {
        return new RuleNode() {
            @Override
            public EvalResult eval(RuleContext context) {
                for (int i = 0; i < properties; i++) {
                    context.setProperty(branch + "-" + i, i);
                }
                return new EvalResult(expr(), true);
            }

            @Override
            public String expr() {
                return "contextWriter" + branch;
            }
        };
    }

    @Test
    public void testHitsNodeDetails() {
        User root = new User("jack", 19);
        RuleNode node = NodeBuilder.build("hits(2,2,t1,f1,t2)");
        NodeResult nodeResult = new RuleEvaluator(ruleEngine).eval(node, root);
        assertEquals(true, nodeResult.getResult());
        assertEquals(3, nodeResult.getDetails().get(0).getSubRules().size());

        assertEquals(true, new RuleEvaluator(ruleEngine)
                .eval(NodeBuilder.build("hits(2,_,t1,f1,t2)"), root).getResult());
        assertEquals(true, new RuleEvaluator(ruleEngine)
                .eval(NodeBuilder.build("hits(_,2,t1,f1,t2)"), root).getResult());
        assertEquals(true, new RuleEvaluator(ruleEngine)
                .eval(NodeBuilder.build("hits(1,2,t1,f1,t2)"), root).getResult());
        assertEquals(false, new RuleEvaluator(ruleEngine)
                .eval(NodeBuilder.build("hits(_,1,t1,f1,t2)"), root).getResult());
    }

    @Test
    public void testHitsNodeShortCircuit() {
        User root = new User("jack", 19);
        RuleEvaluator evaluator = new RuleEvaluator(ruleEngine);

        NodeResult minReached = evaluator.eval(NodeBuilder.build("hits(1,_,t1,f1,t2)"), root);
        assertEquals(true, minReached.getResult());
        assertEquals(1, minReached.getDetails().get(0).getSubRules().size());

        NodeResult maxExceeded = evaluator.eval(NodeBuilder.build("hits(_,1,t1,t2,f1)"), root);
        assertEquals(false, maxExceeded.getResult());
        assertEquals(2, maxExceeded.getDetails().get(0).getSubRules().size());

        NodeResult minUnreachable = evaluator.eval(NodeBuilder.build("hits(3,3,f1,t1,f1)"), root);
        assertEquals(false, minUnreachable.getResult());
        assertEquals(1, minUnreachable.getDetails().get(0).getSubRules().size());
    }

    @Test
    public void tesIfElse() {
        RuleNode actionNode = NodeBuilder.build("a?b:c");
        System.out.println(actionNode.toString());
        RuleEngine.RuleEngineBuilder builder = RuleEngine.builder()
                .ruleDefinitions(Arrays.asList(
                        new RuleDefinition("a", "true", "规则1"),
                        new RuleDefinition("b", "false", "规则2"),
                        new RuleDefinition("c", "sayHello($.name);", "业务操作"),
                        new RuleDefinition("d", "'d'", "规则2")
                ))
                .udfDefinitions(Arrays.asList(
                        new UdfDefinition("sayHello", new SayHelloUdf())
                ));
        User root = new User("jack", 19);
        NodeResult nodeResult = new RuleEvaluator(builder.build()).eval(actionNode, root);
        System.out.println(nodeResult);

    }


}
