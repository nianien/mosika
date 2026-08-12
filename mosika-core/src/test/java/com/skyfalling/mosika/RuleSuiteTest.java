package com.skyfalling.mosika;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.eval.result.RuleResult;
import com.skyfalling.mosika.exception.RuleEvalException;
import com.skyfalling.mosika.suite.RuleSuite;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleSuiteTest {

    @Test
    void compilesAndEvaluatesNamedCompositeRule() {
        RuleSuite suite = new RuleSuite(
                List.of(
                        new RuleDefinition("ready", "true", "ready"),
                        composite("entry", "ready")),
                List.of());

        assertEquals(true, suite.evalRule("entry", new Object()).getResult());

        RuleEvalException exception = assertThrows(
                RuleEvalException.class,
                () -> suite.evalRule("missing", new Object()));
        assertEquals("unregistered rule:missing", exception.getMessage());
    }

    @Test
    void ruleSuitesKeepCompositeRuleParsingIsolated() {
        RuleSuite active = new RuleSuite(
                List.of(
                        new RuleDefinition("activeLeaf", "true", "active leaf"),
                        composite("composite", "activeLeaf")),
                List.of());
        RuleSuite candidate = new RuleSuite(
                List.of(
                        new RuleDefinition("candidateLeaf", "false", "candidate leaf"),
                        composite("composite", "candidateLeaf")),
                List.of());

        assertEquals(true, active.evalRule("composite", new Object()).getResult());
        assertEquals(false, candidate.evalRule("composite", new Object()).getResult());
    }

    @Test
    void compositeRuleCanReferenceLaterCompositeRule() {
        RuleSuite suite = new RuleSuite(
                List.of(
                        composite("entry", "nested"),
                        composite("nested", "leaf"),
                        new RuleDefinition("leaf", "true", "leaf")),
                List.of());

        NodeResult result = suite.evalRule("entry", new Object());

        assertEquals(true, result.getResult());
        assertEquals("nested[leaf]", result.getDetails().get(0)
                .getSubRules().get(0).getExpr());
    }

    @Test
    void recursiveCompositeKeepsFullDetails() {
        RuleSuite suite = new RuleSuite(
                List.of(
                        new RuleDefinition("leaf", "true", "leaf"),
                        composite("caller", "child"),
                        composite("child", "leaf")),
                List.of());

        NodeResult result = suite.evalRule("caller", new Object());

        assertEquals(true, result.getResult());
        RuleResult child = result.getDetails().get(0).getSubRules().get(0);
        assertEquals("child[leaf]", child.getExpr());
        assertEquals("leaf", child.getSubRules().get(0).getExpr());
    }

    @Test
    void compositeCyclesAreRejectedDuringConstruction() {
        assertThrows(IllegalStateException.class, () -> new RuleSuite(
                List.of(composite("self", "self")), List.of()));
        assertThrows(IllegalStateException.class, () -> new RuleSuite(
                List.of(
                        composite("first", "second"),
                        composite("second", "first")),
                List.of()));
    }

    @Test
    void duplicateRuleIdsAreRejectedDuringConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new RuleSuite(
                List.of(
                        new RuleDefinition("duplicate", "true", "first"),
                        new RuleDefinition("duplicate", "false", "second")),
                List.of()));
    }

    @Test
    void parameterizedRuleBindsArgumentsForEachInvocation() {
        RuleSuite suite = new RuleSuite(
                List.of(new RuleDefinition(
                        "threshold",
                        "$.input > $args.limit",
                        "input=${$.input},limit=${$args.limit}")),
                List.of());

        NodeResult result = suite.eval(
                "threshold(\"\"\"{\"limit\":100}\"\"\")||threshold(\"\"\"{\"limit\":20}\"\"\")",
                Map.of("input", 50));

        assertEquals(true, result.getResult());
        List<RuleResult> invocations = result.getDetails().get(0).getSubRules();
        assertEquals(2, invocations.size());
        assertEquals("threshold(\"\"\"{\"limit\":100}\"\"\")", invocations.get(0).getExpr());
        assertEquals(false, invocations.get(0).getResult());
        assertEquals("input=50,limit=100", invocations.get(0).getDesc());
        assertEquals("threshold(\"\"\"{\"limit\":20}\"\"\")", invocations.get(1).getExpr());
        assertEquals(true, invocations.get(1).getResult());
        assertEquals("input=50,limit=20", invocations.get(1).getDesc());
    }

    @Test
    void repeatedParameterizedRuleReusesLeafResultByArguments() {
        RuleSuite suite = new RuleSuite(
                List.of(new RuleDefinition(
                        "increment",
                        "$$.setProperty('count', ($$.getProperty('count') || 0) + $args.step)",
                        "increment")),
                List.of());
        Map<String, Object> context = new HashMap<>();
        String expression =
                "increment(\"\"\"{\"step\":1}\"\"\")->increment(\"\"\"{\"step\":1}\"\"\")";

        suite.eval(expression, null, context);
        NodeResult detailedResult = suite.eval(expression, null);

        assertEquals(1, context.get("count"));
        assertEquals(2, detailedResult.getDetails().get(0).getSubRules().size());
    }

    @Test
    void contextExecutionCanKeepDetails() {
        RuleSuite suite = new RuleSuite(
                List.of(
                        new RuleDefinition(
                                "remember",
                                "$$.setProperty('visited', true)",
                                "remember"),
                        composite("entry", "remember")),
                List.of());
        Map<String, Object> context = new HashMap<>();

        NodeResult result = suite.evalWithDetails("entry", null, context);

        assertEquals(true, context.get("visited"));
        assertEquals("entry[remember]", result.getDetails().get(0).getExpr());
        assertEquals("remember", result.getDetails().get(0).getSubRules().get(0).getExpr());
    }

    @Test
    void parameterizedRuleCacheComparesNestedJsonStructure() {
        RuleSuite suite = new RuleSuite(
                List.of(new RuleDefinition(
                        "increment",
                        "$$.setProperty('count', ($$.getProperty('count') || 0) + $args.step)",
                        "increment")),
                List.of());
        String first = "increment(\"\"\"{\"step\":1,\"user\":{\"name\":\"Tom\","
                + "\"address\":{\"city\":\"Hangzhou\",\"code\":310000}},"
                + "\"roles\":[{\"name\":\"admin\",\"level\":1}]}\"\"\")";
        String reordered = "increment(\"\"\"{\"roles\":[{\"level\":1,\"name\":\"admin\"}],"
                + "\"user\":{\"address\":{\"code\":310000,\"city\":\"Hangzhou\"},"
                + "\"name\":\"Tom\"},\"step\":1}\"\"\")";
        String reversedArray = "increment(\"\"\"{\"step\":1,\"items\":[1,2]}\"\"\")"
                + "->increment(\"\"\"{\"items\":[2,1],\"step\":1}\"\"\")";
        Map<String, Object> reorderedContext = new HashMap<>();
        Map<String, Object> reversedArrayContext = new HashMap<>();

        suite.eval(first + "->" + reordered, null, reorderedContext);
        suite.eval(reversedArray, null, reversedArrayContext);

        assertEquals(1, reorderedContext.get("count"));
        assertEquals(2, reversedArrayContext.get("count"));
    }

    @Test
    void unparameterizedRuleReceivesEmptyArguments() {
        RuleSuite suite = new RuleSuite(
                List.of(new RuleDefinition("plain", "$args.limit == null", "plain")),
                List.of());

        assertEquals(true, suite.evalRule("plain", null).getResult());
    }

    private static RuleDefinition composite(String ruleId, String expression) {
        return new RuleDefinition(
                ruleId, expression, ruleId, RuleDefinition.RULE_TYPE_COMPOSITE);
    }
}
