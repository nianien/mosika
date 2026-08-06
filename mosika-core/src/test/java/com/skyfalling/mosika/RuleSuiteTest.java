package com.skyfalling.mosika;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.eval.result.RuleResult;
import com.skyfalling.mosika.exception.RuleEvalException;
import com.skyfalling.mosika.suite.RuleSuite;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    private static RuleDefinition composite(String ruleId, String expression) {
        return new RuleDefinition(
                ruleId, expression, ruleId, RuleDefinition.RULE_TYPE_COMPOSITE);
    }
}
