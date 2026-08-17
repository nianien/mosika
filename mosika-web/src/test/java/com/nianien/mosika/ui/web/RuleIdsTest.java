package com.nianien.mosika.ui.web;

import com.nianien.mosika.ui.web.common.RuleIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 派生规则 ID 的格式和类型隔离测试 */
class RuleIdsTest {

    @Test
    void formatsDatabaseIdsWithTypePrefix() {
        assertEquals("r1", RuleIds.ruleId(1L));
        assertEquals("f1", RuleIds.flowId(1L));
        assertEquals("r10001", RuleIds.ruleId(10001L));
        assertEquals("f20001", RuleIds.flowId(20001L));
    }

    @Test
    void parsesOnlyCanonicalIdsOfExpectedType() {
        assertEquals(1L, RuleIds.parseRuleId("r1"));
        assertEquals(1L, RuleIds.parseFlowId("f1"));
        assertThrows(IllegalArgumentException.class, () -> RuleIds.parseRuleId("f1"));
        assertThrows(IllegalArgumentException.class, () -> RuleIds.parseFlowId("r1"));
        assertThrows(IllegalArgumentException.class, () -> RuleIds.parseRuleId("1"));
        assertThrows(IllegalArgumentException.class, () -> RuleIds.parseFlowId("1"));
        assertThrows(IllegalArgumentException.class, () -> RuleIds.parseRuleId("r0"));
        assertThrows(IllegalArgumentException.class, () -> RuleIds.parseRuleId("r01"));
        assertThrows(IllegalArgumentException.class,
                () -> RuleIds.parseRuleId("r999999999999999999999999"));
    }

    @Test
    void recognizesRuleAndFlowIdsWithoutCrossTypeFallback() {
        assertTrue(RuleIds.isRuleId("r42"));
        assertTrue(RuleIds.isFlowId("f42"));
        assertFalse(RuleIds.isRuleId("f42"));
        assertFalse(RuleIds.isFlowId("r42"));
    }
}
