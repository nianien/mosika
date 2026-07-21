package com.skyfalling.mousika;

import com.skyfalling.mousika.engine.RuleDefinition;
import com.skyfalling.mousika.suite.RuleSuite;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleSuiteTest {

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
        assertNotNull(constructed.get().getScenes());
        assertSame(constructed.get(), RuleSuite.get());
    }
}
