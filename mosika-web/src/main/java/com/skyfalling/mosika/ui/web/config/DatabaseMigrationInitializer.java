package com.skyfalling.mosika.ui.web.config;

import org.springframework.boot.autoconfigure.sql.init.SqlDataSourceScriptDatabaseInitializer;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

@Component
public class DatabaseMigrationInitializer extends SqlDataSourceScriptDatabaseInitializer {

    private final JdbcTemplate jdbc;

    public DatabaseMigrationInitializer(DataSource dataSource, SqlInitializationProperties properties) {
        super(dataSource, properties);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        migrateUdfDefinition();
        migrateRuleFlow();
        super.afterPropertiesSet();
    }

    private void migrateUdfDefinition() {
        Integer tableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='udf_definition'",
                Integer.class);
        if (tableCount == null || tableCount == 0) {
            return;
        }
        List<String> columns = jdbc.query(
                "PRAGMA table_info(udf_definition)",
                (rs, i) -> rs.getString("name"));
        if (columns.contains("namespace_id")) {
            return;
        }
        new ResourceDatabasePopulator(new ClassPathResource("migration.sql")).execute(getDataSource());
    }

    private void migrateRuleFlow() {
        Integer tableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='rule_flow'",
                Integer.class);
        if (tableCount == null || tableCount == 0) {
            return;
        }
        List<String> columns = jdbc.query(
                "PRAGMA table_info(rule_flow)",
                (rs, i) -> rs.getString("name"));
        if (columns.contains("flow_key")) {
            return;
        }
        jdbc.execute("ALTER TABLE rule_flow ADD COLUMN flow_key INTEGER");
        jdbc.update("UPDATE rule_flow SET flow_key=id, version=1");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_rule_flow_version "
                + "ON rule_flow(flow_key, version)");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_rule_flow_active "
                + "ON rule_flow(flow_key) WHERE status=1");
    }
}
