package com.skyfalling.mosika.ui.web;

import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.eval.result.RuleResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyfalling.mosika.ui.web.service.RuleSuiteManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容生成领域演示数据的可执行验收测试。
 * <p>
 * SQL 不仅要能重复导入，还必须能够装配成完整 RuleSuite，并实际执行三类生成流程。
 */
@SpringBootTest
class ContentGenerationDemoDataTest {

    private static final Path DB_PATH = createDatabasePath();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("mosika.db.path", () -> DB_PATH.toString());
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + DB_PATH + "?foreign_keys=on&busy_timeout=5000");
    }

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RuleSuiteManager suiteManager;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contentGenerationFixtureIsIdempotentBuildableAndExecutable() {
        importDemoData();

        assertEquals(64, count("rule_definition"));
        assertEquals(7, count("rule_flow"));
        assertEquals(127, count("flow_rule_ref"));
        assertEquals(
                "{stage:\"CLAIM_EXTRACTION\",status:\"completed\",claimCount:$.claimCount}",
                jdbc.queryForObject("SELECT expression FROM rule_definition WHERE id=10118", String.class));
        assertEquals(0, flowVersionSum());
        assertTrue(flowDepth(20002) >= 11);
        assertTrue(flowDepth(20003) >= 10);
        assertTrue(flowDepth(20004) >= 10);
        assertTrue(flowDepth(20005) >= 8);
        assertTrue(maxStructuralWidth(20002) <= 5);
        assertTrue(maxStructuralWidth(20003) <= 5);
        assertTrue(maxStructuralWidth(20004) <= 5);
        assertTrue(maxStructuralWidth(20005) <= 5);

        suiteManager.refresh();

        Map<String, Object> target = validTarget("ARTICLE");
        assertExecuted(suiteManager.evalFlow(20001, target, Map.of()));
        assertExecuted(suiteManager.evalFlow(20002, target, Map.of()));
        assertExecuted(suiteManager.evalFlow(20005, target, Map.of()));
        assertExecuted(suiteManager.evalFlow(20006, target, Map.of()));
        assertExecuted(suiteManager.evalFlow(20007, target, Map.of()));
        assertExecutedRule(suiteManager.evalFlow(20005, target, Map.of()), "10108");

        target.put("regulatedTopic", true);
        assertExecutedRule(suiteManager.evalFlow(20005, target, Map.of()), "10130");
        target.put("regulatedTopic", false);
        target.put("exposureLevel", "HIGH");
        assertExecutedRule(suiteManager.evalFlow(20005, target, Map.of()), "10131");
        target.put("exposureLevel", "NORMAL");
        target.put("requiresHumanReview", true);
        assertExecutedRule(suiteManager.evalFlow(20005, target, Map.of()), "10107");
        target.put("requiresHumanReview", false);

        target.put("citedClaimCount", 5);
        assertExecutedRule(suiteManager.evalFlow(20006, target, Map.of()), "10129");
        target.put("citedClaimCount", 10);
        target.put("channel", "APP");
        assertExecutedRule(suiteManager.evalFlow(20007, target, Map.of()), "10126");
        target.put("channel", "WECHAT");

        target.put("contentType", "NEWS_FLASH");
        assertExecuted(suiteManager.evalFlow(20003, target, Map.of()));

        target.put("contentType", "MARKETING");
        assertExecuted(suiteManager.evalFlow(20004, target, Map.of()));

        importDemoData();
        assertEquals(64, count("rule_definition"));
        assertEquals(7, count("rule_flow"));
        assertEquals(127, count("flow_rule_ref"));
        assertEquals(0, flowVersionSum());
    }

    private void importDemoData() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("demo/content-generation.sql"));
        populator.execute(dataSource);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int flowVersionSum() {
        return jdbc.queryForObject("SELECT COALESCE(SUM(version), 0) FROM rule_flow", Integer.class);
    }

    private int flowDepth(long flowId) {
        return maxNodeDepth(readFlowTree(flowId), 0);
    }

    private int maxNodeDepth(JsonNode node, int parentDepth) {
        int depth = node.isObject() && node.has("type") ? parentDepth + 1 : parentDepth;
        int max = depth;
        for (JsonNode child : node) {
            max = Math.max(max, maxNodeDepth(child, depth));
        }
        return max;
    }

    private int maxStructuralWidth(long flowId) {
        return maxStructuralWidth(readFlowTree(flowId));
    }

    private int maxStructuralWidth(JsonNode node) {
        int max = 0;
        if (node.isObject()) {
            for (String field : new String[]{"branches", "rules"}) {
                JsonNode children = node.get(field);
                if (children != null && children.isArray()) {
                    max = children.size();
                }
            }
        }
        for (JsonNode child : node) {
            max = Math.max(max, maxStructuralWidth(child));
        }
        return max;
    }

    private JsonNode readFlowTree(long flowId) {
        String json = jdbc.queryForObject("SELECT rule_tree FROM rule_flow WHERE id = ?", String.class, flowId);
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("invalid flow tree: " + flowId, e);
        }
    }

    private void assertExecuted(NodeResult result) {
        assertNotNull(result);
        assertNotNull(result.getDetails());
        assertFalse(result.getDetails().isEmpty());
    }

    private void assertExecutedRule(NodeResult result, String ruleId) {
        assertExecuted(result);
        assertTrue(containsRule(result.getDetails(), ruleId), () -> "rule not executed: " + ruleId);
    }

    private boolean containsRule(Iterable<RuleResult> results, String ruleId) {
        for (RuleResult result : results) {
            if (ruleId.equals(result.getExpr()) || containsRule(result.getSubRules(), ruleId)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> validTarget(String contentType) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("topic", "生成式 AI 如何改变知识生产");
        target.put("contentType", contentType);
        target.put("channel", "WECHAT");
        target.put("sourceCount", 6);
        target.put("verifiedSourceCount", 5);
        target.put("topicRelevance", 0.92);
        target.put("copyrightRisk", 0.05);
        target.put("sensitiveRisk", 0.08);
        target.put("factConfidence", 0.94);
        target.put("qualityScore", 0.88);
        target.put("brandToneReady", true);
        target.put("requiresHumanReview", false);
        target.put("eventAgeMinutes", 10);
        target.put("promptTemplateVersion", "content-v3");
        target.put("modelPolicy", "QUALITY_BALANCED");
        target.put("remainingTokenBudget", 50000);
        target.put("estimatedTokenCost", 12000);
        target.put("sourceFreshnessHours", 4);
        target.put("maxSourceAgeHours", 72);
        target.put("claimCount", 10);
        target.put("citedClaimCount", 10);
        target.put("originalityScore", 0.91);
        target.put("regulatedTopic", false);
        target.put("exposureLevel", "NORMAL");
        target.put("seoRequired", true);
        target.put("campaignActive", true);
        target.put("campaignBudgetRemaining", 10000);
        target.put("autoPublishEnabled", true);
        return target;
    }

    private static Path createDatabasePath() {
        try {
            Path directory = Files.createTempDirectory("mosika-content-demo-test-");
            return directory.resolve("mosika.db");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
