package com.skyfalling.mosika.ui.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyfalling.mosika.suite.RuleSuite;
import com.skyfalling.mosika.ui.web.common.RuleIds;
import com.skyfalling.mosika.ui.web.service.RuleSuiteManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MosikaWebIntegrationTest {

    private static final Path DB_PATH = createDatabasePath();
    private static final String EMPTY_TREE = "{\"type\":\"T\",\"next\":{\"type\":\"A\",\"rule\":{\"type\":\"R\",\"expr\":\"∅\"}}}";
    private static final String INVALID_DECISION_TREE = "{\"type\":\"T\",\"next\":{\"type\":\"D\",\"branches\":[],\"defaultBranch\":{\"type\":\"A\",\"rule\":{\"type\":\"R\",\"expr\":\"∅\"}}}}";
    private static final String OPTIONAL_ACTION_DECISION_TREE = "{\"type\":\"T\",\"next\":{\"type\":\"D\",\"branches\":[{\"type\":\"C\",\"rule\":{\"type\":\"B\",\"expr\":\"true\"}},{\"type\":\"C\",\"rule\":{\"type\":\"B\",\"expr\":\"false\"}}]}}";
    private static final String EMPTY_SERIAL_TREE = "{\"type\":\"T\",\"next\":{\"type\":\"S\",\"branches\":[]}}";
    private static final String SINGLE_LOGIC_TREE = "{\"type\":\"T\",\"next\":{\"type\":\"C\",\"rule\":{\"type\":\"L\",\"expr\":\"&&\",\"rules\":[{\"type\":\"B\",\"expr\":\"true\"}]}}}";

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("mosika.db.path", () -> DB_PATH.toString());
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH + "?foreign_keys=on&busy_timeout=5000");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RuleSuiteManager suiteManager;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM flow_atomic_ref");
        jdbc.update("DELETE FROM flow_flow_ref");
        jdbc.update("DELETE FROM rule_flow");
        jdbc.update("DELETE FROM atomic_rule");
        jdbc.update("DELETE FROM udf_definition");
        jdbc.update("DELETE FROM rule_namespace WHERE code<>'default'");
        suiteManager.refresh();
    }

    @Test
    void invalidRuleRollsBackWithoutReplacingRuntimeSuite() throws Exception {
        RuleSuite active = suiteManager.getSuite("default");

        mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("bad", "("))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM atomic_rule", Integer.class));
        assertSame(active, suiteManager.getSuite("default"));
    }

    @Test
    void namespaceIsolatesRuntimeSuitePerNamespace() throws Exception {
        mvc.perform(post("/api/namespaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "sales", "name", "销售"))))
                .andExpect(status().isOk());
        RuleSuite initialSalesSuite = suiteManager.getSuite("sales");
        assertSame(initialSalesSuite, suiteManager.getSuite("sales"));

        String defaultRule = createRule("default-condition", "condition");
        Map<String, Object> salesFlow = flowBody("sales-flow", judgeTree(defaultRule), null);
        salesFlow.put("namespace", "sales");
        JsonNode created = data(mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(salesFlow)))
                .andExpect(status().isOk())
                .andReturn());
        String flowId = created.path("flowId").asText();
        salesFlow.put("version", 1);

        mvc.perform(post("/api/flows/{flowId}/publish", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(salesFlow)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("same namespace")));

        String childFlowId = createFlow("default-child", EMPTY_TREE);
        mvc.perform(post("/api/flows/{flowId}/publish", childFlowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("default-child", EMPTY_TREE, 1))))
                .andExpect(status().isOk());
        Map<String, Object> salesCaller = flowBody(
                "sales-caller", compositeReferenceTree(childFlowId), null);
        salesCaller.put("namespace", "sales");
        String callerFlowId = data(mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(salesCaller)))
                .andExpect(status().isOk())
                .andReturn()).path("flowId").asText();
        salesCaller.put("version", 1);
        mvc.perform(post("/api/flows/{flowId}/publish", callerFlowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(salesCaller)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("same namespace")));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM atomic_rule", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM rule_flow", Integer.class));
        RuleSuite defaultSuite = suiteManager.getSuite("default");
        RuleSuite salesSuite = suiteManager.getSuite("sales");
        assertNotSame(defaultSuite, salesSuite);
        assertSame(defaultSuite, suiteManager.getSuite("default"));
        assertSame(salesSuite, suiteManager.getSuite("sales"));
    }

    @Test
    void actionObjectLiteralDoesNotRequireParentheses() throws Exception {
        Map<String, Object> body = ruleBody(
                "extract-claims",
                "{stage:'CLAIM_EXTRACTION',status:'completed',claimCount:$.claimCount}");
        body.put("kind", "action");
        String ruleId = data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expression")
                        .value("{stage:'CLAIM_EXTRACTION',status:'completed',claimCount:$.claimCount}"))
                .andReturn()).path("ruleId").asText();

        mvc.perform(post("/api/eval/rule/{ruleId}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"claimCount\":3}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.stage").value("CLAIM_EXTRACTION"))
                .andExpect(jsonPath("$.data.result.status").value("completed"))
                .andExpect(jsonPath("$.data.result.claimCount").value(3));
    }

    @Test
    void activeRuleReferencesIncludeExpressionForTestInputExtraction() throws Exception {
        String expression = "$.customer.name == $$.tenant.name";
        String ruleId = data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("test-inputs", expression))))
                .andExpect(status().isOk())
                .andReturn()).path("ruleId").asText();

        mvc.perform(get("/api/rules/references")
                        .param("namespace", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ruleId").value(ruleId))
                .andExpect(jsonPath("$.data[0].expression").value(expression));
    }

    @Test
    void userCanRegisterUpdateDisableAndEnableParameterizedUdf() throws Exception {
        Map<String, Object> create = udfBody("content.generation", "bindCitations",
                "(target, prefix) => ({stage: prefix, citedClaimCount: target.citedClaimCount})");
        JsonNode created = data(mvc.perform(post("/api/udfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andReturn());
        long id = created.path("id").asLong();

        Map<String, Object> action = ruleBody("bind-citations",
                "content.generation.bindCitations($, 'CITATION_BINDING')");
        action.put("kind", "action");
        String ruleId = data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(action)))
                .andExpect(status().isOk())
                .andReturn()).path("ruleId").asText();

        mvc.perform(post("/api/eval/rule/{ruleId}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"citedClaimCount\":7}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.stage").value("CITATION_BINDING"))
                .andExpect(jsonPath("$.data.result.citedClaimCount").value(7));

        Map<String, Object> update = udfBody("content.generation", "bindCitations", """
                function bindCitations(target, prefix) {
                    return {stage: prefix, citedClaimCount: target.citedClaimCount * 2};
                }
                """);
        update.put("version", created.path("version").asLong());
        JsonNode updated = data(mvc.perform(put("/api/udfs/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andReturn());

        mvc.perform(post("/api/eval/rule/{ruleId}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"citedClaimCount\":7}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.stage").value("CITATION_BINDING"))
                .andExpect(jsonPath("$.data.result.citedClaimCount").value(14));

        mvc.perform(delete("/api/udfs/{id}", id).param("version", updated.path("version").asText()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/udfs").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(id));

        mvc.perform(post("/api/udfs/{id}/enable", id).param("version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));
    }

    @Test
    void udfRegistrationRejectsInvalidReservedAndDuplicateDefinitions() throws Exception {
        mvc.perform(post("/api/udfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(udfBody("content", "notFunction", "1 + 2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM udf_definition", Integer.class));

        mvc.perform(post("/api/udfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(udfBody("sys.flow", "custom", "() => true"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/udfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(udfBody("$", "overrideTarget", "() => true"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/udfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(udfBody("$args", "overrideArguments", "() => true"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/udfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(udfBody("", "class", "() => true"))))
                .andExpect(status().isBadRequest());

        Map<String, Object> valid = udfBody("content", "normalize", "value => value");
        mvc.perform(post("/api/udfs").contentType(MediaType.APPLICATION_JSON).content(json(valid)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/udfs").contentType(MediaType.APPLICATION_JSON).content(json(valid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void udfPathAndEvaluationAreIsolatedByNamespace() throws Exception {
        mvc.perform(post("/api/namespaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "sales", "name", "销售"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/udfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(udfBody("scope", "value", "target => 'default'"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.namespace").value("default"))
                .andExpect(jsonPath("$.data.namespaceId").doesNotExist());

        Map<String, Object> salesUdf = udfBody("scope", "value", "target => 'sales'");
        salesUdf.put("namespace", "sales");
        mvc.perform(post("/api/udfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(salesUdf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.namespace").value("sales"));

        String defaultRuleId = data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("默认域取值", "scope.value($)"))))
                .andExpect(status().isOk()).andReturn()).path("ruleId").asText();

        Map<String, Object> salesRule = ruleBody("销售域取值", "scope.value($)");
        salesRule.put("namespace", "sales");
        String salesRuleId = data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(salesRule)))
                .andExpect(status().isOk()).andReturn()).path("ruleId").asText();

        mvc.perform(post("/api/eval/rule/{ruleId}", defaultRuleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("target", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("default"));
        mvc.perform(post("/api/eval/rule/{ruleId}", salesRuleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("target", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("sales"));

        mvc.perform(post("/api/eval/expr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "namespace", "sales",
                                "expression", salesRuleId,
                                "target", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("sales"));
        mvc.perform(post("/api/eval/expr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "namespace", "sales",
                                "expression", defaultRuleId,
                                "target", Map.of()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("unregistered rule:" + defaultRuleId));
        mvc.perform(post("/api/eval/expr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expression", defaultRuleId,
                                "target", Map.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("namespace is required"));
    }

    @Test
    void runtimeRuleFailureUsesHttp400() throws Exception {
        String ruleId = data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("执行期失败", "$.missing.deep"))))
                .andExpect(status().isOk()).andReturn()).path("ruleId").asText();

        mvc.perform(post("/api/eval/rule/{ruleId}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("target", Map.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("rule evaluation failed: " + ruleId));
    }

    @Test
    void namespaceDisableRequiresEmptyContentAndRemovesRuntimeSuite() throws Exception {
        mvc.perform(post("/api/namespaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "sales", "name", "销售"))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/namespaces/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "销售规则", "description", "销售域"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("销售规则"));

        Map<String, Object> salesFlow = flowBody("sales-flow", EMPTY_TREE, null);
        salesFlow.put("namespace", "sales");
        String flowId = data(mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(salesFlow)))
                .andExpect(status().isOk())
                .andReturn()).path("flowId").asText();
        salesFlow.put("version", 1);
        mvc.perform(post("/api/flows/{flowId}/publish", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(salesFlow)))
                .andExpect(status().isOk());

        JsonNode namespaces = data(mvc.perform(get("/api/namespaces"))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode sales = null;
        for (JsonNode namespace : namespaces) {
            if ("sales".equals(namespace.path("code").asText())) {
                sales = namespace;
            }
        }
        assertEquals(0, sales.path("ruleCount").asInt());
        assertEquals(1, sales.path("flowCount").asInt());
        assertEquals(0, sales.path("udfCount").asInt());

        mvc.perform(post("/api/namespaces/sales/disable"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("rules=0, flows=1, udfs=0")));
        mvc.perform(post("/api/namespaces/default/disable"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be disabled")));

        long flowKey = RuleIds.parseFlowId(flowId);
        long flowDatabaseId = jdbc.queryForObject(
                "SELECT id FROM rule_flow WHERE flow_key=?", Long.class, flowKey);
        jdbc.update("DELETE FROM flow_atomic_ref WHERE flow_id=?", flowDatabaseId);
        jdbc.update("DELETE FROM flow_flow_ref WHERE flow_id=?", flowDatabaseId);
        jdbc.update("DELETE FROM rule_flow WHERE id=?", flowDatabaseId);

        mvc.perform(post("/api/namespaces/sales/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        jdbc.update("""
                INSERT INTO rule_flow
                    (flow_key, namespace_id, name, description, rule_tree, status, version)
                VALUES (?, (SELECT id FROM rule_namespace WHERE code='sales'),
                        'disabled-flow', 'disabled-flow', ?, 1, 1)
                """, flowKey, EMPTY_TREE);
        mvc.perform(post("/api/eval/flow/{flowId}", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("namespace not found or disabled: sales")));

        mvc.perform(post("/api/namespaces/sales/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));
        mvc.perform(post("/api/eval/flow/{flowId}", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void staleRuleVersionIsRejectedAndVersionIsRequiredForTransitions() throws Exception {
        JsonNode created = data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("r1", "true"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.namespaceId").doesNotExist())
                .andReturn());
        String ruleId = created.path("ruleId").asText();
        assertTrue(ruleId.startsWith("r"));

        Map<String, Object> update = ruleBody("r1-updated", "true");
        update.put("version", 0);
        mvc.perform(put("/api/rules/{ruleId}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(put("/api/rules/{ruleId}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        mvc.perform(delete("/api/rules/{ruleId}", ruleId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void flowLifecycleKeepsMultipleDraftsAndPublishesOneVersion() throws Exception {
        JsonNode created = data(mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow", EMPTY_TREE, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.namespaceId").doesNotExist())
                .andReturn());
        String flowId = created.path("flowId").asText();
        assertTrue(flowId.startsWith("f"));

        mvc.perform(post("/api/flows/{flowId}/publish", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow", INVALID_DECISION_TREE, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mvc.perform(post("/api/flows/{flowId}/publish", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow", EMPTY_TREE, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(put("/api/flows/{flowId}", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow", EMPTY_TREE, 1))))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/flows/{flowId}/versions", flowId).param("baseVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(post("/api/flows/{flowId}/versions", flowId).param("baseVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.version").value(3));

        mvc.perform(put("/api/flows/{flowId}", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow-v2", EMPTY_TREE, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(post("/api/flows/{flowId}/publish", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow-v2", EMPTY_TREE, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(get("/api/flows/{flowId}/versions", flowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].version").value(3))
                .andExpect(jsonPath("$.data[0].status").value(0))
                .andExpect(jsonPath("$.data[1].version").value(2))
                .andExpect(jsonPath("$.data[1].status").value(1))
                .andExpect(jsonPath("$.data[2].version").value(1))
                .andExpect(jsonPath("$.data[2].status").value(3));

        mvc.perform(post("/api/eval/flow/{flowId}", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void publishAcceptsDecisionBranchesWithoutActions() throws Exception {
        String flowId = createFlow("optional-actions", OPTIONAL_ACTION_DECISION_TREE);

        mvc.perform(post("/api/flows/{flowId}/publish", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("optional-actions", OPTIONAL_ACTION_DECISION_TREE, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(post("/api/eval/flow/{flowId}", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void flowTryExecutesUnsavedTreeAndReturnsVisualPaths() throws Exception {
        Map<String, Object> action = ruleBody(
                "remember-preview",
                "$$.setProperty('visited', $.value)");
        action.put("kind", "action");
        String actionRuleId = data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(action)))
                .andExpect(status().isOk())
                .andReturn()).path("ruleId").asText();
        RuleSuite active = suiteManager.getSuite("default");
        String tree = """
                {"type":"T","next":{"type":"D","branches":[
                  {"type":"C","rule":{"type":"B","expr":"false"},
                   "next":{"type":"A","rule":{"type":"R","expr":"∅"}}},
                  {"type":"C","rule":{"type":"B","expr":"true"},
                   "next":{"type":"A","rule":{"type":"R","expr":"%s"}}}
                ],"defaultBranch":{"type":"A","rule":{"type":"R","expr":"∅"}}}}
                """.formatted(actionRuleId);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("namespace", "default");
        request.put("ruleTree", tree);
        request.put("target", Map.of("value", "done"));
        request.put("context", Map.of());

        mvc.perform(post("/api/eval/flow/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.context.visited").value("done"))
                .andExpect(jsonPath("$.data.executedPaths[0]").value("$"))
                .andExpect(jsonPath("$.data.executedPaths[1]").value("$.next"))
                .andExpect(jsonPath("$.data.executedPaths[2]").value("$.next.branches[0]"))
                .andExpect(jsonPath("$.data.executedPaths[3]").value("$.next.branches[1]"))
                .andExpect(jsonPath("$.data.executedPaths[4]").value("$.next.branches[1].next"))
                .andExpect(jsonPath("$.data.executedPaths.length()").value(5));

        assertSame(active, suiteManager.getSuite("default"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rule_flow", Integer.class));
    }

    @Test
    void publishedReferenceKeepsCalleeDetailsForRendering() throws Exception {
        String childTree = judgeTree("true");
        String childFlowId = createFlow("child", childTree);
        mvc.perform(post("/api/flows/{flowId}/publish", childFlowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("child", childTree, 1))))
                .andExpect(status().isOk());

        String callerTree = compositeReferenceTree(childFlowId);
        String callerFlowId = createFlow("caller", callerTree);
        mvc.perform(post("/api/flows/{flowId}/publish", callerFlowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("caller", callerTree, 1))))
                .andExpect(status().isOk());

        var result = suiteManager.evalFlow(callerFlowId, new Object(), Map.of());
        assertEquals(true, result.getResult());
        assertEquals(1, result.getDetails().size());
        assertEquals(callerFlowId + "[" + childFlowId + "]",
                result.getDetails().get(0).getExpr());
        var reference = result.getDetails().get(0).getSubRules().get(0);
        assertTrue(reference.getExpr().startsWith(childFlowId + "["));
        assertFalse(reference.getSubRules().isEmpty());

        mvc.perform(delete("/api/flows/{flowId}", childFlowId).param("version", "1"))
                .andExpect(status().isOk());
        assertEquals(true, suiteManager.evalFlow(callerFlowId, new Object(), Map.of()).getResult());
        mvc.perform(post("/api/eval/flow/{flowId}", childFlowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

    }

    @Test
    void updatingDisabledReferencedRuleRefreshesRuntimeSuite() throws Exception {
        Map<String, Object> action = ruleBody("action", "'before'");
        action.put("kind", "action");
        JsonNode created = data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(action)))
                .andExpect(status().isOk())
                .andReturn());
        String ruleId = created.path("ruleId").asText();

        String tree = actionTree(ruleId);
        String flowId = createFlow("flow", tree);
        mvc.perform(post("/api/flows/{flowId}/publish", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow", tree, 1))))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/rules/{ruleId}", ruleId).param("version", "0"))
                .andExpect(status().isOk());
        assertEquals("before", suiteManager.evalFlow(flowId, new Object(), Map.of()).getResult());

        Map<String, Object> update = ruleBody("action", "'after'");
        update.put("kind", "action");
        update.put("version", 1);
        mvc.perform(put("/api/rules/{ruleId}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        assertEquals("after", suiteManager.evalFlow(flowId, new Object(), Map.of()).getResult());
    }

    @Test
    void splitTableIdsDoNotCollideInCoreRuleNamespace() {
        long sharedId = 42;
        jdbc.update("""
                INSERT INTO atomic_rule
                    (id, namespace_id, name, description, expression, kind, status, version)
                VALUES (?, (SELECT id FROM rule_namespace WHERE code='default'),
                        'same-id-rule', 'same-id-rule', 'false', 'condition', 1, 0)
                """, sharedId);
        jdbc.update("""
                INSERT INTO rule_flow
                    (id, flow_key, namespace_id, name, description, rule_tree, status, version)
                VALUES (?, ?, (SELECT id FROM rule_namespace WHERE code='default'),
                        'same-id-flow', 'same-id-flow', ?, 1, 1)
                """, sharedId, sharedId, judgeTree("true"));

        suiteManager.refresh();

        assertEquals(false, suiteManager.evalRule("r42", new Object()).getResult());
        assertEquals(true, suiteManager.evalFlow("f42", new Object(), Map.of()).getResult());
    }

    @Test
    void typedIdsRejectWrongPrefixAndPlainNumbers() throws Exception {
        mvc.perform(get("/api/rules/f1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("invalid ruleId")));
        mvc.perform(get("/api/rules/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("invalid ruleId")));
        mvc.perform(get("/api/flows/r1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("invalid flowId")));
        mvc.perform(get("/api/flows/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("invalid flowId")));
    }

    @Test
    void missingResourcesAndEntitiesUseHttp404() throws Exception {
        mvc.perform(get("/api/flows/f999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        mvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void webRoutesUseSpringBootStaticResources() throws Exception {
        mvc.perform(get("/ui/console.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"newFlowBtn\"")));
        mvc.perform(get("/ui/rules.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"newRuleBtn\"")));
        mvc.perform(get("/ui/udfs.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"createBtn\"")));
        mvc.perform(get("/ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"treeRoot\"")));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/ui/console.html"));
        mvc.perform(get("/rules"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/ui/rules.html"));
        mvc.perform(get("/udfs"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/ui/udfs.html"));
        mvc.perform(get("/flow/f12"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/ui/index.html"));
    }

    @Test
    void malformedRequestsUseHttp400() throws Exception {
        mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/flows").param("status", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void publishRejectsUnknownReferencesAndInvalidStructures() throws Exception {
        String unknownFlowId = createFlow("unknown", judgeTree("bogus"));
        mvc.perform(post("/api/flows/{flowId}/publish", unknownFlowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("unknown", judgeTree("bogus"), 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        String embeddedDslFlowId = createFlow("embedded-dsl", judgeTree("true&&false"));
        mvc.perform(post("/api/flows/{flowId}/publish", embeddedDslFlowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("embedded-dsl", judgeTree("true&&false"), 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        String invalidBuiltinFlowId = createFlow("invalid-builtin", actionTree("true"));
        mvc.perform(post("/api/flows/{flowId}/publish", invalidBuiltinFlowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("invalid-builtin", actionTree("true"), 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        for (String invalidTree : java.util.List.of(EMPTY_SERIAL_TREE, SINGLE_LOGIC_TREE)) {
            String flowId = createFlow("invalid", invalidTree);
            mvc.perform(post("/api/flows/{flowId}/publish", flowId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(flowBody("invalid", invalidTree, 1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    @Test
    void publishEnforcesRuleKindAtNodePosition() throws Exception {
        String actionRule = createRule("action", "action");
        String conditionRule = createRule("condition", "condition");

        String wrongJudge = createFlow("wrong-judge", judgeTree(actionRule));
        mvc.perform(post("/api/flows/{flowId}/publish", wrongJudge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("wrong-judge", judgeTree(actionRule), 1))))
                .andExpect(status().isBadRequest());

        String wrongAction = createFlow("wrong-action", actionTree(conditionRule));
        mvc.perform(post("/api/flows/{flowId}/publish", wrongAction)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("wrong-action", actionTree(conditionRule), 1))))
                .andExpect(status().isBadRequest());

        String valid = createFlow("valid", actionTree(actionRule));
        mvc.perform(post("/api/flows/{flowId}/publish", valid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("valid", actionTree(actionRule), 1))))
                .andExpect(status().isOk());
    }

    @Test
    void largePageNumberDoesNotOverflowToFirstPage() throws Exception {
        createFlow("only", EMPTY_TREE);
        mvc.perform(get("/api/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].ruleTree").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].nodeCount").value(3))
                .andExpect(jsonPath("$.data.items[0].referencedRuleIds.length()").value(0));
        mvc.perform(get("/api/flows")
                        .param("pageNumber", String.valueOf(Integer.MAX_VALUE))
                        .param("pageSize", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void explicitRefreshReportsFailureAndKeepsPreviousSnapshot() throws Exception {
        RuleSuite active = suiteManager.getSuite("default");
        String flowId = createFlow("broken-later", EMPTY_TREE);
        mvc.perform(post("/api/flows/{flowId}/publish", flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("broken-later", EMPTY_TREE, 1))))
                .andExpect(status().isOk());
        RuleSuite published = suiteManager.getSuite("default");
        jdbc.update("UPDATE rule_flow SET rule_tree='not-json' WHERE flow_key=? AND status=1",
                RuleIds.parseFlowId(flowId));

        mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("must-rollback", "true"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM atomic_rule", Integer.class));
        assertSame(published, suiteManager.getSuite("default"));

        mvc.perform(post("/api/system/refresh"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
        assertSame(published, suiteManager.getSuite("default"));
        org.junit.jupiter.api.Assertions.assertNotSame(active, published);
    }

    private Map<String, Object> ruleBody(String name, String expression) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("namespace", "default");
        body.put("description", name);
        body.put("expression", expression);
        body.put("kind", "condition");
        return body;
    }

    private Map<String, Object> udfBody(String group, String name, String source) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("namespace", "default");
        body.put("group", group);
        body.put("name", name);
        body.put("description", name);
        body.put("source", source);
        return body;
    }

    private String createRule(String name, String kind) throws Exception {
        Map<String, Object> body = ruleBody(name, "true");
        body.put("kind", kind);
        return data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn()).path("ruleId").asText();
    }

    private String createFlow(String name, String tree) throws Exception {
        return data(mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody(name, tree, null))))
                .andExpect(status().isOk())
                .andReturn()).path("flowId").asText();
    }

    private String judgeTree(String ruleId) {
        return "{\"type\":\"T\",\"next\":{\"type\":\"C\",\"rule\":{\"type\":\"B\",\"expr\":\""
                + ruleId + "\"}}}";
    }

    private String actionTree(String ruleId) {
        return "{\"type\":\"T\",\"next\":{\"type\":\"A\",\"rule\":{\"type\":\"R\",\"expr\":\""
                + ruleId + "\"}}}";
    }

    private String compositeReferenceTree(String flowId) {
        return "{\"type\":\"T\",\"next\":{\"type\":\"A\",\"rule\":{\"type\":\"R\",\"expr\":\""
                + flowId + "\"}}}";
    }

    private Map<String, Object> flowBody(String name, String tree, Integer version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("namespace", "default");
        body.put("description", name);
        body.put("ruleTree", tree);
        if (version != null) {
            body.put("version", version);
        }
        return body;
    }

    private String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static Path createDatabasePath() {
        try {
            Path directory = Files.createTempDirectory("mosika-web-test-");
            return directory.resolve("mosika.db");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
