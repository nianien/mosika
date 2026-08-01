package com.skyfalling.mousika.ui.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyfalling.mousika.suite.RuleSuite;
import com.skyfalling.mousika.ui.web.service.RuleSuiteManager;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MousikaWebIntegrationTest {

    private static final Path DB_PATH = createDatabasePath();
    private static final String EMPTY_TREE = "{\"type\":\"T\",\"expr\":\"\",\"next\":{\"type\":\"A\",\"expr\":\"∅\"}}";
    private static final String INVALID_DECISION_TREE = "{\"type\":\"T\",\"expr\":\"\",\"next\":{\"type\":\"D\",\"expr\":\"D\",\"branches\":[],\"action\":{\"type\":\"A\",\"expr\":\"∅\"}}}";
    private static final String EMPTY_SERIAL_TREE = "{\"type\":\"T\",\"expr\":\"\",\"next\":{\"type\":\"S\",\"expr\":\"S\",\"branches\":[]}}";
    private static final String SINGLE_LOGIC_TREE = "{\"type\":\"T\",\"expr\":\"\",\"next\":{\"type\":\"J\",\"expr\":\"J\",\"rule\":{\"type\":\"L\",\"expr\":\"&&\",\"rules\":[{\"type\":\"R\",\"expr\":\"true\"}]}}}";

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("mousika.db.path", () -> DB_PATH.toString());
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
        jdbc.update("DELETE FROM flow_rule_ref");
        jdbc.update("DELETE FROM rule_flow");
        jdbc.update("DELETE FROM rule_definition");
        suiteManager.refresh();
    }

    @Test
    void invalidRuleRollsBackWithoutReplacingRuntimeSuite() throws Exception {
        RuleSuite active = suiteManager.getSuite();

        mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("bad", "("))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rule_definition", Integer.class));
        assertSame(active, suiteManager.getSuite());
    }

    @Test
    void staleRuleVersionIsRejectedAndVersionIsRequiredForTransitions() throws Exception {
        JsonNode created = data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("r1", "true"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andReturn());
        long id = created.path("id").asLong();

        Map<String, Object> update = ruleBody("r1-updated", "true");
        update.put("version", 0);
        mvc.perform(put("/api/rules/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(put("/api/rules/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        mvc.perform(delete("/api/rules/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void flowLifecycleValidatesDecisionAndRemovesDraftFromRuntime() throws Exception {
        JsonNode created = data(mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow", EMPTY_TREE, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andReturn());
        long id = created.path("id").asLong();

        mvc.perform(post("/api/flows/{id}/publish", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow", INVALID_DECISION_TREE, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mvc.perform(post("/api/flows/{id}/publish", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow", EMPTY_TREE, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(put("/api/flows/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow", EMPTY_TREE, 0))))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/flows/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("flow", EMPTY_TREE, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(post("/api/eval/flow/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void missingResourcesAndEntitiesUseHttp404() throws Exception {
        mvc.perform(get("/api/flows/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        mvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
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
        long unknownId = createFlow("unknown", judgeTree("bogus"));
        mvc.perform(post("/api/flows/{id}/publish", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("unknown", judgeTree("bogus"), 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        long embeddedDslId = createFlow("embedded-dsl", judgeTree("true&&false"));
        mvc.perform(post("/api/flows/{id}/publish", embeddedDslId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("embedded-dsl", judgeTree("true&&false"), 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        long invalidBuiltinId = createFlow("invalid-builtin", actionTree("true"));
        mvc.perform(post("/api/flows/{id}/publish", invalidBuiltinId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("invalid-builtin", actionTree("true"), 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        for (String invalidTree : java.util.List.of(EMPTY_SERIAL_TREE, SINGLE_LOGIC_TREE)) {
            long id = createFlow("invalid", invalidTree);
            mvc.perform(post("/api/flows/{id}/publish", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(flowBody("invalid", invalidTree, 0))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    @Test
    void publishEnforcesRuleKindAtNodePosition() throws Exception {
        long actionRule = createRule("action", "action");
        long conditionRule = createRule("condition", "condition");

        long wrongJudge = createFlow("wrong-judge", judgeTree(String.valueOf(actionRule)));
        mvc.perform(post("/api/flows/{id}/publish", wrongJudge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("wrong-judge", judgeTree(String.valueOf(actionRule)), 0))))
                .andExpect(status().isBadRequest());

        long wrongAction = createFlow("wrong-action", actionTree(String.valueOf(conditionRule)));
        mvc.perform(post("/api/flows/{id}/publish", wrongAction)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("wrong-action", actionTree(String.valueOf(conditionRule)), 0))))
                .andExpect(status().isBadRequest());

        long valid = createFlow("valid", actionTree(String.valueOf(actionRule)));
        mvc.perform(post("/api/flows/{id}/publish", valid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("valid", actionTree(String.valueOf(actionRule)), 0))))
                .andExpect(status().isOk());
    }

    @Test
    void largePageNumberDoesNotOverflowToFirstPage() throws Exception {
        createFlow("only", EMPTY_TREE);
        mvc.perform(get("/api/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].ruleTree").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].nodeCount").value(2))
                .andExpect(jsonPath("$.data.items[0].referencedRuleIds.length()").value(0));
        mvc.perform(get("/api/flows")
                        .param("pageNumber", String.valueOf(Integer.MAX_VALUE))
                        .param("pageSize", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void explicitRefreshReportsFailureAndKeepsPreviousSnapshot() throws Exception {
        RuleSuite active = suiteManager.getSuite();
        long id = createFlow("broken-later", EMPTY_TREE);
        mvc.perform(post("/api/flows/{id}/publish", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody("broken-later", EMPTY_TREE, 0))))
                .andExpect(status().isOk());
        RuleSuite published = suiteManager.getSuite();
        jdbc.update("UPDATE rule_flow SET rule_tree='not-json' WHERE id=?", id);

        mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("must-rollback", "true"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rule_definition", Integer.class));
        assertSame(published, suiteManager.getSuite());

        mvc.perform(post("/api/system/refresh"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
        assertSame(published, suiteManager.getSuite());
        org.junit.jupiter.api.Assertions.assertNotSame(active, published);
    }

    private Map<String, Object> ruleBody(String name, String expression) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", name);
        body.put("expression", expression);
        body.put("useType", 0);
        body.put("ruleKind", "condition");
        return body;
    }

    private long createRule(String name, String kind) throws Exception {
        Map<String, Object> body = ruleBody(name, "true");
        body.put("ruleKind", kind);
        return data(mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn()).path("id").asLong();
    }

    private long createFlow(String name, String tree) throws Exception {
        return data(mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(flowBody(name, tree, null))))
                .andExpect(status().isOk())
                .andReturn()).path("id").asLong();
    }

    private String judgeTree(String ruleId) {
        return "{\"type\":\"T\",\"expr\":\"\",\"next\":{\"type\":\"J\",\"expr\":\"J\",\"rule\":{\"type\":\"R\",\"expr\":\""
                + ruleId + "\"}}}";
    }

    private String actionTree(String ruleId) {
        return "{\"type\":\"T\",\"expr\":\"\",\"next\":{\"type\":\"A\",\"expr\":\""
                + ruleId + "\"}}";
    }

    private Map<String, Object> flowBody(String name, String tree, Integer version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
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
            Path directory = Files.createTempDirectory("mousika-web-test-");
            return directory.resolve("mousika.db");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
