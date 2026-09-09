package com.nianien.mosika.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nianien.mosika.ui.web.MosikaWebApplication;
import com.nianien.mosika.ui.web.service.RuleSuiteManager;
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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MosikaWebApplication.class)
@AutoConfigureMockMvc
class MosikaJavaUdfIntegrationTest {

    private static final Path DB_PATH = createDatabasePath();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("mosika.db.path", () -> DB_PATH.toString());
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + DB_PATH + "?foreign_keys=on&busy_timeout=5000");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

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
    void mergesReadOnlyBuiltinUdfWithEditableDatabaseUdf() throws Exception {
        mvc.perform(post("/api/udfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "namespace":"default",
                                  "group":"jsFns",
                                  "name":"increment",
                                  "description":"increment",
                                  "source":"value => value + 1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("javascript"))
                .andExpect(jsonPath("$.data.editable").value(true));

        // 内置 sys.crypto.md5 是只读的 Java UDF，按方法关键字过滤到唯一条目
        mvc.perform(get("/api/udfs")
                        .param("namespace", "default")
                        .param("status", "1")
                        .param("keyword", "md5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].group").value("sys"))
                .andExpect(jsonPath("$.data.items[0].name").value("crypto"))
                .andExpect(jsonPath("$.data.items[0].type").value("java"))
                .andExpect(jsonPath("$.data.items[0].editable").value(false))
                .andExpect(jsonPath("$.data.items[0].implementationKind").value("bean"))
                .andExpect(jsonPath("$.data.items[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].methods[0].name").value("md5"))
                .andExpect(jsonPath("$.data.items[0].methods[0].parameterNames[0]").value("text"))
                .andExpect(jsonPath("$.data.items[0].methods[0].returnType").value("String"))
                .andExpect(jsonPath("$.data.items[0].methods[0].description").value("MD5 十六进制摘要"));

        // 可编辑的数据库 JavaScript UDF 与内置 Java UDF 并列呈现
        mvc.perform(get("/api/udfs")
                        .param("namespace", "default")
                        .param("status", "1")
                        .param("keyword", "increment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].group").value("jsFns"))
                .andExpect(jsonPath("$.data.items[0].name").value("increment"))
                .andExpect(jsonPath("$.data.items[0].type").value("javascript"))
                .andExpect(jsonPath("$.data.items[0].editable").value(true))
                .andExpect(jsonPath("$.data.items[0].methods").doesNotExist());

        // 规则同时调用内置 Java UDF 与数据库 JavaScript UDF
        String createRuleResponse = mvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "namespace":"default",
                                  "name":"combined-udf",
                                  "description":"combined-udf",
                                  "expression":"sys.crypto.sha256('abc').length === 64 ? jsFns.increment($.value) : 0",
                                  "kind":"action"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ruleId = mapper.readTree(createRuleResponse)
                .path("data").path("ruleId").asText();

        mvc.perform(post("/api/eval/rule/{ruleId}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"value\":3}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value(4));

        // 保留分组 sys 拒绝创建同名 JavaScript UDF
        mvc.perform(post("/api/udfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "namespace":"default",
                                  "group":"sys",
                                  "name":"num",
                                  "description":"conflict",
                                  "source":"value => value"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("udf group 'sys' is reserved for built-in functions"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM udf_definition", Integer.class));
    }

    private static Path createDatabasePath() {
        try {
            Path directory = Files.createTempDirectory("mosika-java-udf-test-");
            return directory.resolve("mosika.db");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
