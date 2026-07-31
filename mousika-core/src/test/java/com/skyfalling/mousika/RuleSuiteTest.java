package com.skyfalling.mousika;

import com.skyfalling.mousika.engine.RuleDefinition;
import com.skyfalling.mousika.exception.NoRuleFlowException;
import com.skyfalling.mousika.suite.RuleFlowDefinition;
import com.skyfalling.mousika.suite.RuleSuite;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleSuiteTest {

    @Test
    void compilesAndEvaluatesNamedRuleFlow() {
        RuleSuite suite = new RuleSuite(
                List.of(new RuleDefinition("ready", "true", "ready")),
                List.of(),
                List.of(new RuleFlowDefinition("flow", "ready"))
        );

        assertEquals("ready", suite.getRuleFlow("flow").getRoot().expr());
        assertEquals(true, suite.evalFlow("flow", new Object()).getResult());

        NoRuleFlowException exception = assertThrows(
                NoRuleFlowException.class,
                () -> suite.evalFlow("missing", new Object())
        );
        assertEquals("missing", exception.getFlowId());
    }

    @Test
    void publishesCurrentAfterConstruction() throws InterruptedException {
        RuleSuite previous = RuleSuite.get();
        CountDownLatch constructionStarted = new CountDownLatch(1);
        CountDownLatch continueConstruction = new CountDownLatch(1);
        AtomicReference<RuleSuite> constructed = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        List<RuleDefinition> blockingRules = new AbstractList<>() {
            @Override
            public RuleDefinition get(int index) {
                constructionStarted.countDown();
                try {
                    if (!continueConstruction.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to continue RuleSuite construction");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return new RuleDefinition("ready", "true", "ready");
            }

            @Override
            public int size() {
                return 1;
            }
        };

        Thread constructor = new Thread(() -> {
            try {
                constructed.set(new RuleSuite(blockingRules, List.of(), List.of()));
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        constructor.start();

        assertTrue(constructionStarted.await(5, TimeUnit.SECONDS));
        assertSame(previous, RuleSuite.get());

        continueConstruction.countDown();
        constructor.join(5_000);

        assertFalse(constructor.isAlive());
        assertNull(failure.get());
        assertNotNull(constructed.get());
        assertNotNull(constructed.get().getRuleEvaluator());
        assertNotNull(constructed.get().getFlows());
        assertSame(constructed.get(), RuleSuite.get());
    }
}
