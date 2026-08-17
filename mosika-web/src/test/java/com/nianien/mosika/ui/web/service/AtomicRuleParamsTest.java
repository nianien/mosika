package com.nianien.mosika.ui.web.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原子规则参数模板 {@code validateParams}/{@code parseParams} 的校验与解析边界测试。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class AtomicRuleParamsTest {

    @Test
    public void testBlankNormalizedToEmptyArray() {
        assertEquals("[]", AtomicRuleService.validateParams(null));
        assertEquals("[]", AtomicRuleService.validateParams(""));
        assertEquals("[]", AtomicRuleService.validateParams("   "));
    }

    @Test
    public void testValidParamsPassthroughUnchanged() {
        String params = "[{\"name\":\"limit\",\"label\":\"上限\",\"type\":\"number\",\"default\":100},"
                + "{\"name\":\"mode\",\"type\":\"enum\",\"options\":[\"a\",\"b\"],\"default\":\"a\"}]";
        // 结构合法时原样返回，不改写内部结构（原始 JSON 透传）
        assertEquals(params, AtomicRuleService.validateParams(params));
    }

    @Test
    public void testInvalidJsonRejected() {
        // 非法 JSON 与非数组结构都归一为“不是 JSON 数组”错误，而非泄漏底层解析异常
        assertMessageContains(assertThrows(IllegalArgumentException.class,
                () -> AtomicRuleService.validateParams("not json")), "JSON array");
        assertMessageContains(assertThrows(IllegalArgumentException.class,
                () -> AtomicRuleService.validateParams("{\"name\":\"x\"}")), "JSON array");
    }

    @Test
    public void testElementMustBeObject() {
        assertMessageContains(assertThrows(IllegalArgumentException.class,
                () -> AtomicRuleService.validateParams("[\"limit\"]")), "must be a JSON object");
    }

    @Test
    public void testNameRequiredAndUnique() {
        assertMessageContains(assertThrows(IllegalArgumentException.class,
                () -> AtomicRuleService.validateParams("[{\"type\":\"string\"}]")), "name is required");
        assertMessageContains(assertThrows(IllegalArgumentException.class,
                () -> AtomicRuleService.validateParams("[{\"name\":\"  \",\"type\":\"string\"}]")),
                "name is required");
        assertMessageContains(assertThrows(IllegalArgumentException.class,
                () -> AtomicRuleService.validateParams(
                        "[{\"name\":\"x\",\"type\":\"string\"},{\"name\":\"x\",\"type\":\"number\"}]")),
                "duplicate param name");
    }

    @Test
    public void testTypeMustBeAllowed() {
        assertMessageContains(assertThrows(IllegalArgumentException.class,
                () -> AtomicRuleService.validateParams("[{\"name\":\"x\",\"type\":\"date\"}]")),
                "param type must be one of");
        // type 缺失同样按非法类型拒绝
        assertMessageContains(assertThrows(IllegalArgumentException.class,
                () -> AtomicRuleService.validateParams("[{\"name\":\"x\"}]")),
                "param type must be one of");
    }

    @Test
    public void testEnumRequiresNonEmptyOptions() {
        assertMessageContains(assertThrows(IllegalArgumentException.class,
                () -> AtomicRuleService.validateParams("[{\"name\":\"x\",\"type\":\"enum\"}]")),
                "requires non-empty options");
        assertMessageContains(assertThrows(IllegalArgumentException.class,
                () -> AtomicRuleService.validateParams("[{\"name\":\"x\",\"type\":\"enum\",\"options\":[]}]")),
                "requires non-empty options");
        // 带非空 options 的 enum 合法
        String ok = "[{\"name\":\"x\",\"type\":\"enum\",\"options\":[\"a\"]}]";
        assertEquals(ok, AtomicRuleService.validateParams(ok));
    }

    @Test
    public void testParseParamsStructuresValidAndFallsBackOtherwise() {
        List<?> parsed = AtomicRuleService.parseParams(
                "[{\"name\":\"limit\",\"type\":\"number\"},{\"name\":\"mode\",\"type\":\"string\"}]");
        assertEquals(2, parsed.size());

        // 空、非法 JSON 都静默回退为空列表，避免打断规则引用索引
        assertTrue(AtomicRuleService.parseParams(null).isEmpty());
        assertTrue(AtomicRuleService.parseParams("   ").isEmpty());
        assertTrue(AtomicRuleService.parseParams("not json").isEmpty());
        assertTrue(AtomicRuleService.parseParams("{\"name\":\"x\"}").isEmpty());
    }

    private void assertMessageContains(Throwable error, String fragment) {
        String message = String.valueOf(error.getMessage());
        assertTrue(message.contains(fragment),
                "expected message to contain '" + fragment + "' but was: " + message);
    }
}
