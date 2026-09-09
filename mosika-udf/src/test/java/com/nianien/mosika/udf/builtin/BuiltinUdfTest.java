package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.eval.result.NodeResult;
import com.nianien.mosika.suite.RuleSuite;
import com.nianien.mosika.udf.UdfDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置 UDF 的注册与端到端调用验证。
 */
class BuiltinUdfTest {

    @Test
    void definitionsAllUnderReservedSysGroup() {
        List<UdfDefinition> definitions = BuiltinUdfs.definitions();
        assertEquals(6, definitions.size());
        assertTrue(definitions.stream().allMatch(d -> "sys".equals(d.getGroup())));
    }

    @Test
    void numKeepsDecimalPrecision() {
        assertEquals("0.3", result("sys.num.add(\"0.1\", \"0.2\")"));
        assertEquals("0.3333", result("sys.num.divide(\"1\", \"3\", 4)"));
        assertEquals("25.00", result("sys.num.percent(\"1\", \"4\", 2)"));
    }

    @Test
    void cryptoProducesJavaOnlyDigests() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                result("sys.crypto.sha256(\"abc\")"));
        assertEquals("aGVsbG8=", result("sys.crypto.base64Encode(\"hello\")"));
        assertEquals("hello", result("sys.crypto.base64Decode(\"aGVsbG8=\")"));
    }

    @Test
    void dateUsesJavaTime() {
        assertEquals("30", result("sys.date.daysBetween(\"2026-01-01\", \"2026-01-31\")"));
        assertEquals("2026-02-01", result("sys.date.plusDays(\"2026-01-31\", 1)"));
        assertEquals("4", result("sys.date.dayOfWeek(\"2026-01-01\")"));
    }

    @Test
    void textUsesJavaRegex() {
        assertEquals("true", result("sys.text.matches(\"abc123\", \"[a-z]+[0-9]+\")"));
        assertEquals("a-b-c", result("sys.text.replaceAll(\"a.b.c\", \"\\\\.\", \"-\")"));
    }

    @Test
    void collAggregatesLists() {
        assertEquals("6", result("sys.coll.sum([\"1\", \"2\", \"3\"])"));
        assertEquals("3", result("sys.coll.size([\"1\", \"2\", \"3\"])"));
        assertEquals("true", result("sys.coll.contains([\"a\", \"b\"], \"b\")"));
    }

    @Test
    void idGeneratesUuid() {
        String uuid = result("sys.id.uuid()");
        assertTrue(uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    private static String result(String expression) {
        RuleSuite suite = new RuleSuite(
                List.of(new RuleDefinition("r", expression, "r")),
                BuiltinUdfs.definitions());
        NodeResult result = suite.eval("r", null);
        return String.valueOf(result.getResult());
    }
}
