package com.nianien.mosika;

import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.suite.RuleSuite;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * 规则引擎端到端整体压测:构建一次 {@link RuleSuite},压其执行路径。
 * <p>
 * 对照两种 body:并行 {@code =>} 与全串行 {@code ->},以归因并发扩展瓶颈。
 * 手动运行:{@code mvn -pl mosika-core test -Dtest=RuleEngineBenchmark}
 */
public class RuleEngineBenchmark {

    private static RuleDefinition atomic(String id, String js) {
        return new RuleDefinition(id, js, id);
    }

    private static RuleDefinition composite(String id, String dsl) {
        return new RuleDefinition(id, dsl, id, RuleDefinition.RULE_TYPE_COMPOSITE);
    }

    private static RuleSuite buildSuite(String bodyDsl) {
        return new RuleSuite(List.of(
                atomic("isAdult", "$.age >= 18"),
                atomic("isVip", "$.level >= 3"),
                atomic("hasBalance", "$.balance >= $args.min"),
                atomic("discount", "$.amount * 0.9"),
                atomic("greet", "'hi ' + $.name"),
                atomic("recordVisit", "($$.put('n', ($$.get('n')||0)+1), true)"),
                composite("gate", "isAdult && isVip"),
                composite("body", bodyDsl),
                composite("fallback", "recordVisit"),
                composite("flow", "gate ? body : fallback")
        ), List.of());
    }

    private static Map<String, Object> input() {
        Map<String, Object> in = new HashMap<>();
        in.put("age", 20);
        in.put("level", 5);
        in.put("balance", 100);
        in.put("amount", 200);
        in.put("name", "tom");
        return in;
    }

    public static void main(String[] args) throws Exception {
        new RuleEngineBenchmark().benchmark();
    }

    @Test
    public void benchmark() throws Exception {
        String parallel = "hasBalance(\"\"\"{\"min\":50}\"\"\") -> (discount => greet) -> recordVisit";
        String serial = "hasBalance(\"\"\"{\"min\":50}\"\"\") -> discount -> greet -> recordVisit";
        measure("并行 =>", buildSuite(parallel));
        measure("全串行 ->", buildSuite(serial));
    }

    private void measure(String label, RuleSuite suite) throws InterruptedException {
        Map<String, Object> in = input();
        System.out.println("\n================ " + label + " ================");
        System.out.println("smoke  flow result = " + suite.eval("flow", in, new HashMap<>()).getResult());

        for (int i = 0; i < 20000; i++) {
            suite.eval("flow", in, new HashMap<>());
        }

        com.sun.management.ThreadMXBean tb =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        tb.setThreadAllocatedMemoryEnabled(true);
        int allocIters = 50000;
        long b0 = totalAlloc(tb);
        for (int i = 0; i < allocIters; i++) {
            suite.eval("flow", in, new HashMap<>());
        }
        long perEval = (totalAlloc(tb) - b0) / allocIters;
        System.out.printf("每次 eval 分配  %,d 字节  (%.1f KB)%n", perEval, perEval / 1024.0);

        int iters = 100000;
        long[] lat = new long[iters];
        long start = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            suite.eval("flow", in, new HashMap<>());
            lat[i] = System.nanoTime() - t0;
        }
        long dur = System.nanoTime() - start;
        Arrays.sort(lat);
        System.out.printf(
                "单线程  %,d evals  %.2fs  →  %,.0f ops/s   avg %.1fµs  p50 %.1fµs  p99 %.1fµs  max %.1fµs%n",
                iters, dur / 1e9, iters / (dur / 1e9),
                (dur / (double) iters) / 1000, lat[iters / 2] / 1000.0,
                lat[(int) (iters * 0.99)] / 1000.0, lat[iters - 1] / 1000.0);

        int cores = Runtime.getRuntime().availableProcessors();
        int perThread = 60000;
        double base = 0;
        for (int threads : new int[]{1, 2, 4, cores}) {
            double ops = runConcurrent(suite, in, threads, perThread);
            if (threads == 1) {
                base = ops;
            }
            System.out.printf("并发 %2d 线程  →  %,.0f ops/s   加速比 %.2fx%n", threads, ops, ops / base);
        }
    }

    private static long totalAlloc(com.sun.management.ThreadMXBean tb) {
        long sum = 0;
        for (long id : tb.getAllThreadIds()) {
            long a = tb.getThreadAllocatedBytes(id);
            if (a > 0) {
                sum += a;
            }
        }
        return sum;
    }

    private static double runConcurrent(RuleSuite suite, Map<String, Object> in,
                                        int threads, int perThread) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                for (int i = 0; i < perThread; i++) {
                    suite.eval("flow", in, new HashMap<>());
                }
                done.countDown();
            }).start();
        }
        ready.await();
        long start = System.nanoTime();
        go.countDown();
        done.await();
        long dur = System.nanoTime() - start;
        return (long) threads * perThread / (dur / 1e9);
    }
}
