package com.skyfalling.mousika;

import com.cudrania.core.utils.TimeCounter;
import com.skyfalling.mousika.engine.RuleDefinition;
import com.skyfalling.mousika.engine.RuleEngine;
import com.skyfalling.mousika.engine.UdfDefinition;
import com.skyfalling.mousika.utils.JsRuntime;
import lombok.SneakyThrows;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created on 2023/3/31
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class RuleEngineTest {

    @SneakyThrows
    @Test
    public void ruleRuleEngine2() {
        Source source = JsRuntime.createSource("$.value + 1", "concurrent-source");
        List<CompletableFuture<Integer>> futures = IntStream.range(0, 10)
                .mapToObj(value -> CompletableFuture.supplyAsync(() -> {
                    try (Context context = JsRuntime.createContext()) {
                        context.getBindings(JsRuntime.LANGUAGE_ID)
                                .putMember("$", Map.of("value", value));
                        return context.eval(source).asInt();
                    }
                }))
                .toList();
        for (int i = 0; i < futures.size(); i++) {
            assertEquals(i + 1, futures.get(i).join());
        }
    }


    /**
     * 测试js函数
     */
    @SneakyThrows
    @Test
    public void testJsUdf0() {
        RuleEngine.RuleEngineBuilder builder = RuleEngine.builder();
        builder.udfDefinition(new UdfDefinition("jdUdf", "test", """
                function test(begin,end) {
                    var sum = begin; for (var i = 0; i < end; i++) {
                        sum = sum + i % 8
                    }
                    return sum;
                }
                """));
        RuleEngine engine = builder.build();
        Object object = engine.evalExpr("jdUdf.test(1001,10000)", null, null);
        System.out.println(object);
    }

    /**
     * 测试多线程下的js函数
     */
    @SneakyThrows
    @Test
    public void testJsUdf() {
        RuleEngine.RuleEngineBuilder builder = RuleEngine.builder();
        builder.udfDefinition(new UdfDefinition("jdUdf", "test", """
                function test(begin) {
                    var sum = begin; for (var i = 0; i < 1000000; i++) {
                        sum = sum + i % 8
                    }
                    return sum;
                }
                """));
        RuleEngine engine = builder.build();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int begin = i;
            Thread thread = new Thread(() -> {
                Object object = engine.evalExpr("jdUdf.test(" + begin + ")", null, null);
                System.out.println(object);
            });
            thread.start();
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }


    @Test
    public void testDesc() {
        RuleEngine ruleEngine = RuleEngine.builder().build();
        String desc = "代理商【{$.agentId}】不允许【{$.customerId}】跨开{}";
        desc = "\"" + desc.replaceAll("\\{(\\$+\\..+?)\\}", "\\\"+$1+\\\"") + "\"";
        System.out.println(desc);
        Map<String, String> map = new HashMap<>();
        map.put("agentId", "a");
        map.put("customerId", "b");
        System.out.println(ruleEngine.evalExpr(desc, map, null));
        assertEquals("代理商【a】不允许【b】跨开{}", ruleEngine.evalExpr(desc, map, null));
    }

    @Test
    public void testDescriptionEscaping() {
        String desc = "用户“{$.name}”状态：\"{$$.status}\"\n路径 C:\\temp";
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
    public void testNumberTypeEval() {
        RuleEngine ruleEngine = RuleEngine.builder().build();
        Map<String, Object> map = new HashMap<>();
        map.put("currentMonth", 5);
        Object res = ruleEngine.evalExpr("$.currentMonth*1.0", map, null);
        TimeCounter tc=new TimeCounter();
        int times=10000;
        for (int i = 0; i < times; i++) {
            ruleEngine.evalExpr("$.currentMonth*1.0", map, null);
        }
        System.out.println(tc.timePassed()*1.0/times);

        assertEquals(5, res);

        Object res2 = ruleEngine.evalExpr("""
                ({"result":true, "count":42});
                """, new HashMap<String, Integer>() {
        }, null);
        System.out.println(res2.getClass());
        assertTrue(res2 instanceof Map);
    }
}
