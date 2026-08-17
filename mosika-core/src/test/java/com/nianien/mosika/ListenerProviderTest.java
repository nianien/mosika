package com.nianien.mosika;

import com.nianien.mosika.eval.listener.ListenerProvider;
import com.nianien.mosika.eval.listener.RuleEvent;
import com.nianien.mosika.eval.listener.RuleListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListenerProviderTest {

    @Test
    void listenerFailuresDoNotInterruptOtherListeners() {
        ListenerProvider provider = new ListenerProvider();
        AtomicInteger parseEvents = new AtomicInteger();
        AtomicInteger evalEvents = new AtomicInteger();
        provider.register(new RuleListener() {
            @Override
            public void onParse(RuleEvent event) {
                throw new IllegalStateException("parse observer failed");
            }

            @Override
            public void onEval(RuleEvent event) {
                throw new IllegalStateException("eval observer failed");
            }
        });
        provider.register(new RuleListener() {
            @Override
            public void onParse(RuleEvent event) {
                parseEvents.incrementAndGet();
            }

            @Override
            public void onEval(RuleEvent event) {
                evalEvents.incrementAndGet();
            }
        });

        provider.onParse(null);
        provider.onEval(null);

        assertEquals(1, parseEvents.get());
        assertEquals(1, evalEvents.get());
    }
}
