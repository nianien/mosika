package com.nianien.mosika;

import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.engine.RuleEngine;
import com.nianien.mosika.engine.UdfDefinition;
import com.nianien.mosika.eval.result.EvalResult;
import com.nianien.mosika.udf.Functions.Function1;
import com.nianien.mosika.udf.UdfDelegate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created on 2023/3/31
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class RuleEngineTest {

    @Test
    public void testRuleEvaluationIsThreadSafe() {
        RuleEngine engine = RuleEngine.builder().build();
        List<CompletableFuture<Integer>> futures = IntStream.range(0, 100)
                .mapToObj(value -> CompletableFuture.supplyAsync(
                        () -> (Integer) engine.evalExpr(
                                "$.value + 1", Map.of("value", value), null)))
                .toList();
        for (int i = 0; i < futures.size(); i++) {
            assertEquals(i + 1, futures.get(i).join());
        }
    }

    @Test
    public void testJsUdfAcceptsSupportedFunctionExpressions() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinitions(List.of(
                        new UdfDefinition("math", "arrowSum", "(a, b) => a + b;"),
                        new UdfDefinition("math", "anonymousSum", """
                                function (a, b) {
                                    return a + b;
                                }
                                """),
                        new UdfDefinition("math", "namedSum", """
                                function namedSum(a, b) {
                                    return a + b;
                                }
                                """)))
                .build();

        assertEquals(3, engine.evalExpr("math.arrowSum(1, 2)", null, null));
        assertEquals(7, engine.evalExpr("math.anonymousSum(3, 4)", null, null));
        assertEquals(11, engine.evalExpr("math.namedSum(5, 6)", null, null));
    }

    @Test
    public void testJsUdfKeepsLegacyScriptWithHelperFunctions() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinition(new UdfDefinition("math", "sum", """
                        function normalize(value) {
                            return Number(value);
                        }
                        function sum(a, b) {
                            return normalize(a) + normalize(b);
                        }
                        """))
                .build();

        assertEquals(3, engine.evalExpr("math.sum('1', '2')", null, null));
    }

    @Test
    public void testJsUdfRequiresNamedFunctionToMatchRegistrationName() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RuleEngine.builder()
                        .udfDefinition(new UdfDefinition("math", "sum", """
                                function add(a, b) {
                                    return a + b;
                                }
                                """))
                        .build());

        assertEquals("JavaScript UDF name mismatch: registered as sum but declared as add",
                exception.getMessage());
    }

    @Test
    public void testJsUdfRejectsNonFunctionDuringRegistration() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RuleEngine.builder()
                        .udfDefinition(new UdfDefinition("math", "sum", "1 + 2"))
                        .build());

        assertEquals("JavaScript UDF is not executable: sum", exception.getMessage());
    }

    @Test
    public void testJsUdfsUseTheRuleContext() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinitions(List.of(
                        new UdfDefinition("math", "javaIncrement", new IncrementUdf()),
                        new UdfDefinition("math", "increment", """
                                function increment(value) {
                                    return math.javaIncrement(value);
                                }
                                """),
                        new UdfDefinition("math", "twiceIncrement", """
                                function twiceIncrement(value) {
                                    return math.increment(value) * 2;
                                }
                                """)))
                .build();

        assertEquals(8, engine.evalExpr("math.twiceIncrement(3)", null, null));
    }

    @Test
    public void testJsUdfEvaluationIsThreadSafe() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinition(new UdfDefinition("math", "increment", "value => value + 1"))
                .build();

        List<CompletableFuture<Integer>> futures = IntStream.range(0, 10)
                .mapToObj(value -> CompletableFuture.supplyAsync(
                        () -> (Integer) engine.evalExpr("math.increment($.value)", Map.of("value", value), null)))
                .toList();

        for (int i = 0; i < futures.size(); i++) {
            assertEquals(i + 1, futures.get(i).join());
        }
    }

    @Test
    public void testTopLevelEvalUdfDoesNotOverrideRuleEvaluation() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinition(new UdfDefinition("eval", """
                        function eval(source) {
                            return 'overridden';
                        }
                        """))
                .build();

        assertEquals(3, engine.evalExpr("1 + 2", null, null));
        assertEquals("overridden", engine.evalExpr("eval('1 + 2')", null, null));
        assertEquals(3, engine.evalExpr("const value = 1; value + 2;", null, null));
    }

    @Test
    public void testRuleEvaluationDoesNotShareScriptState() {
        RuleEngine engine = RuleEngine.builder().build();

        assertEquals(1, engine.evalExpr("globalThis.marker = 1", null, null));
        assertEquals("undefined", engine.evalExpr("typeof globalThis.marker", null, null));
        assertEquals(2, engine.evalExpr("const value = 1; value + 1", null, null));
        assertEquals(2, engine.evalExpr("const value = 1; value + 1", null, null));
    }

    @Test
    public void testRuleEvaluationDoesNotSharePrototypeState() {
        RuleEngine engine = RuleEngine.builder().build();

        assertThrows(RuntimeException.class,
                () -> engine.evalExpr("Array.prototype.marker = 1", null, null));
        assertEquals("undefined", engine.evalExpr("typeof [].marker", null, null));
    }

    @Test
    public void testRuleScriptReturnsLastExpression() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinitions(List.of(
                        new UdfDefinition("sep", "value => value"),
                        new UdfDefinition("dist", "(name, separator) => name + separator")))
                .build();

        assertEquals(10, engine.evalExpr(
                "var n = 0; for (var i = 0; i < 5; i++) n += i; n;", null, null));
        assertEquals("ning;", engine.evalExpr(
                "sep(';'); dist($.name, ';');", Map.of("name", "ning"), null));
        assertEquals(2, engine.evalExpr(
                "var s = 'a;b'; s.split(';').length;", null, null));
        assertEquals(1, engine.evalExpr(
                "if ($.age > 18) { var result = 1; } result;", Map.of("age", 20), null));
    }

    @Test
    public void testRuleScriptKeepsStatementCompletionValue() {
        RuleEngine engine = RuleEngine.builder().build();

        assertEquals(2, engine.evalExpr(
                "var result = 1; if ($.age > 18) { result = 2; }",
                Map.of("age", 20),
                null));
    }

    @Test
    public void testGroupedJsUdfsKeepIndependentLexicalScopes() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinitions(List.of(
                        new UdfDefinition("group1", "factorial", """
                                function factorial(n) {
                                    return n <= 1 ? 1 : n * factorial(n - 1);
                                }
                                """),
                        new UdfDefinition("group2", "factorial", """
                                function factorial(n) {
                                    return 100;
                                }
                                """)))
                .build();

        assertEquals(120, engine.evalExpr("group1.factorial(5)", null, null));
        assertEquals(100, engine.evalExpr("group2.factorial(5)", null, null));
        assertEquals(120, engine.evalExpr("group1.factorial(5)", null, null));
    }

    @Test
    public void testDesc() {
        String desc = "代理商【${$.agentId}】不允许【${$.customerId}】跨开{}";
        RuleEngine ruleEngine = RuleEngine.builder()
                .ruleDefinition(new RuleDefinition("agentRule", "true", desc))
                .build();

        String result = ruleEngine.evalRuleDesc(
                "agentRule",
                Map.of("agentId", "a", "customerId", "b"),
                null);

        assertEquals("代理商【a】不允许【b】跨开{}", result);
    }

    @Test
    public void testDescriptionEscaping() {
        String desc = "用户“${$.name}”状态：\"${$$.status}\"\n路径 C:\\temp";
        RuleEngine ruleEngine = RuleEngine.builder()
                .ruleDefinition(new RuleDefinition("escapedDesc", "true", desc))
                .build();

        String result = ruleEngine.evalRuleDesc(
                "escapedDesc",
                Map.of("name", "jack"),
                Map.of("status", "通过"));

        assertEquals("用户“jack”状态：\"通过\"\n路径 C:\\temp", result);
    }

    @Test
    public void testDescriptionSupportsJavaScriptExpression() {
        String desc = "qq${$.name.toUpperCase()}xxx；${$$.agent}；"
                + "${$.name + ({suffix: '}'}).suffix}；${$.code.replace(/a}/g, 'X')}";
        RuleEngine ruleEngine = RuleEngine.builder()
                .ruleDefinition(new RuleDefinition("expressionDesc", "true", desc))
                .build();

        String result = ruleEngine.evalRuleDesc(
                "expressionDesc",
                Map.of("name", "jack", "code", "a}"),
                Map.of("agent", "system"));

        assertEquals("qqJACKxxx；system；jack}；X", result);
    }

    @Test
    public void testDescriptionEvaluationIsThreadSafe() {
        RuleEngine ruleEngine = RuleEngine.builder()
                .ruleDefinition(new RuleDefinition("concurrentDesc", "true", "value=${$.value.toUpperCase()}"))
                .build();

        List<CompletableFuture<String>> futures = IntStream.range(0, 10)
                .mapToObj(value -> CompletableFuture.supplyAsync(() -> ruleEngine.evalRuleDesc(
                        "concurrentDesc", Map.of("value", "v" + value), null)))
                .toList();

        for (int i = 0; i < futures.size(); i++) {
            assertEquals("value=V" + i, futures.get(i).join());
        }
    }

    @Test
    public void testDescriptionKeepsJavaScriptStringConversion() {
        RuleEngine ruleEngine = RuleEngine.builder()
                .ruleDefinition(new RuleDefinition("valueDesc", "true", "null=${$.nullable},number=${$.number}"))
                .build();
        Map<String, Object> data = new HashMap<>();
        data.put("nullable", null);
        data.put("number", 7);

        assertEquals("null=null,number=7", ruleEngine.evalRuleDesc("valueDesc", data, null));
    }

    @Test
    public void testJavaHostValuesRemainAvailable() {
        RuleEngine ruleEngine = RuleEngine.builder().build();
        Map<String, Object> data = Map.of("amount", new BigDecimal("12.50"));

        assertEquals(new BigDecimal("12.50"), ruleEngine.evalExpr("$.amount", data, null));
        assertEquals(new BigDecimal("13.50"), ruleEngine.evalExpr(
                "$.amount.add(new (Java.type('java.math.BigDecimal'))('1.00'))", data, null));
    }

    @Test
    public void testJavaScriptIntegerOutsideLongRangeRemainsDouble() {
        RuleEngine ruleEngine = RuleEngine.builder().build();

        Object upperBound = ruleEngine.evalExpr("9223372036854775808", null, null);

        assertTrue(upperBound instanceof Double);
        assertEquals(Double.valueOf(0x1.0p63), upperBound);
        assertEquals(9223372036854774784L,
                ruleEngine.evalExpr("9223372036854774784", null, null));
    }

    @Test
    public void testJavaScriptSparseArrayHolesBecomeNull() {
        RuleEngine ruleEngine = RuleEngine.builder().build();

        assertEquals(java.util.Arrays.asList(null, 1, null),
                ruleEngine.evalExpr("[, 1, undefined]", null, null));
    }

    @Test
    public void testObjectLiteralExpressionDoesNotRequireParentheses() {
        RuleEngine ruleEngine = RuleEngine.builder()
                .ruleDefinition(new RuleDefinition(
                        "extractClaims",
                        "{stage:'CLAIM_EXTRACTION',status:'completed',claimCount:$.claimCount}",
                        "extract claims"))
                .build();

        assertEquals(
                Map.of("stage", "CLAIM_EXTRACTION", "status", "completed", "claimCount", 3),
                ruleEngine.evalRule("extractClaims", Map.of("claimCount", 3), null));
    }

    @Test
    public void testNumberMatchingKeepsNumberPrecision() {
        assertTrue(new EvalResult("tinyDecimal", new BigDecimal("1E-1000")).isMatched());
        assertTrue(new EvalResult("bigInteger", new BigInteger("999999999999999999999999")).isMatched());
        assertTrue(new EvalResult("tinyDouble", Double.MIN_VALUE).isMatched());
        assertFalse(new EvalResult("zero", BigDecimal.ZERO).isMatched());
        assertFalse(new EvalResult("negative", new BigDecimal("-1E-1000")).isMatched());
    }

    @Test
    public void testNumberTypeEval() {
        RuleEngine ruleEngine = RuleEngine.builder().build();
        Map<String, Object> map = new HashMap<>();
        map.put("currentMonth", 5);
        Object res = ruleEngine.evalExpr("$.currentMonth*1.0", map, null);
        long start = System.nanoTime();
        int times=10000;
        for (int i = 0; i < times; i++) {
            ruleEngine.evalExpr("$.currentMonth*1.0", map, null);
        }
        System.out.println((System.nanoTime() - start) / 1_000_000.0 / times);

        assertEquals(5, res);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testJavaUdfExceptionPropagatesUnwrapped() {
        UdfDelegate delegate = UdfDelegate.of(new BoomUdf());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> delegate.apply(1));
        assertEquals("boom", exception.getMessage());
    }

    public static class IncrementUdf implements Function1<Integer, Integer> {

        @Override
        public Integer apply(Integer value) {
            return value + 1;
        }
    }

    public static class BoomUdf implements Function1<Integer, Integer> {

        @Override
        public Integer apply(Integer value) {
            throw new IllegalStateException("boom");
        }
    }
}
