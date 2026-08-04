package com.skyfalling.mosika;

import com.skyfalling.mosika.bean.User;
import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.engine.RuleEngine;
import com.skyfalling.mosika.engine.UdfDefinition;
import com.skyfalling.mosika.eval.RuleVisitor;
import com.skyfalling.mosika.eval.context.RuleContext;
import com.skyfalling.mosika.eval.node.ExprNode;
import com.skyfalling.mosika.eval.node.ParNode;
import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.eval.node.SerNode;
import com.skyfalling.mosika.eval.parser.NodeBuilder;
import com.skyfalling.mosika.eval.result.EvalResult;
import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.exception.RuleEvalException;
import com.skyfalling.mosika.suite.RuleEvaluator;
import com.skyfalling.mosika.suite.RuleFlowDefinition;
import com.skyfalling.mosika.suite.RuleSuite;
import com.skyfalling.mosika.udf.SayHelloUdf;
import com.skyfalling.mosika.utils.Constants;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    public void testConcurrentDuplicateRuleEvaluatedOnce() throws Exception {
        int concurrency = 8;
        AtomicInteger executions = new AtomicInteger();
        CyclicBarrier callers = new CyclicBarrier(concurrency);
        CyclicBarrier evaluations = new CyclicBarrier(concurrency);
        RuleEngine engine = new RuleEngine(List.of(), List.of()) {
            @Override
            public Object evalRule(String ruleId, Object root, Object context) {
                executions.incrementAndGet();
                try {
                    evaluations.await(1, TimeUnit.SECONDS);
                } catch (TimeoutException expected) {
                    // ConcurrentHashMap only allows one mapping function to execute for this key.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                } catch (BrokenBarrierException e) {
                    throw new AssertionError(e);
                }
                return true;
            }
        };
        RuleVisitor context = new RuleVisitor(engine, null);
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        try {
            List<Future<EvalResult>> results = IntStream.range(0, concurrency)
                    .mapToObj(i -> executor.submit(() -> {
                        callers.await(5, TimeUnit.SECONDS);
                        return context.eval("shared");
                    }))
                    .collect(Collectors.toList());
            for (Future<EvalResult> result : results) {
                assertTrue(result.get(5, TimeUnit.SECONDS).isMatched());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, executions.get());
    }

    @Test
    public void testParallelBranchKeepsRuleEvalExceptionContract() {
        RuleEngine engine = RuleEngine.builder()
                .ruleDefinition(new RuleDefinition("boom",
                        "Java.type('java.lang.Integer').parseInt('x')", "boom"))
                .build();

        RuleEvalException exception = assertThrows(RuleEvalException.class,
                () -> new RuleEvaluator(engine).eval(NodeBuilder.build("boom=>true"), null));

        assertEquals("boom", exception.getRuleId());
    }

    @Test
    public void testInterruptedParallelExecutionCancelsPendingBranches() throws InterruptedException {
        int parallelism = ForkJoinPool.getCommonPoolParallelism();
        CountDownLatch workersStarted = new CountDownLatch(parallelism);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        List<CompletableFuture<Void>> blockers = IntStream.range(0, parallelism)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    workersStarted.countDown();
                    try {
                        releaseWorkers.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, ForkJoinPool.commonPool()))
                .collect(Collectors.toList());
        AtomicInteger executions = new AtomicInteger();
        RuleNode pending = new RuleNode() {
            @Override
            public EvalResult eval(RuleContext context) {
                executions.incrementAndGet();
                return new EvalResult(expr(), null, true);
            }

            @Override
            public String expr() {
                return "pending";
            }
        };

        try {
            assertTrue(workersStarted.await(5, TimeUnit.SECONDS));
            Thread.currentThread().interrupt();

            assertThrows(RuleEvalException.class,
                    () -> new ParNode(pending).eval(new RuleVisitor(ruleEngine, null)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            releaseWorkers.countDown();
            blockers.forEach(CompletableFuture::join);
        }
        assertTrue(ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS));
        assertEquals(0, executions.get());
    }

    @Test
    public void testDescriptionUsesTheRuleBeingMaterialized() {
        RuleDefinition composite = new RuleDefinition("composite", "a&&b",
                "desc-rule=${$$.rule}", 2);
        RuleSuite suite = new RuleSuite(
                List.of(
                        new RuleDefinition("a", "true", "desc-rule=${$$.rule}"),
                        new RuleDefinition("b", "true", "desc-rule=${$$.rule}"),
                        composite),
                List.of(),
                List.of(
                        new RuleFlowDefinition("serial", "a->b"),
                        new RuleFlowDefinition("parallel", "a=>b"),
                        new RuleFlowDefinition("composite", "composite")));

        Map<String, String> serialDescriptions = suite.evalFlow("serial", null)
                .getDetails().get(0).getSubRules().stream()
                .collect(Collectors.toMap(EvalResult::getExpr, result -> result.getDesc()));
        Map<String, String> parallelDescriptions = suite.evalFlow("parallel", null)
                .getDetails().get(0).getSubRules().stream()
                .collect(Collectors.toMap(EvalResult::getExpr, result -> result.getDesc()));

        assertEquals(Map.of("a", "desc-rule=a", "b", "desc-rule=b"), serialDescriptions);
        assertEquals(Map.of("a", "desc-rule=a", "b", "desc-rule=b"), parallelDescriptions);
        assertEquals("desc-rule=composite", suite.evalFlow("composite", null)
                .getDetails().get(0).getDesc());
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
