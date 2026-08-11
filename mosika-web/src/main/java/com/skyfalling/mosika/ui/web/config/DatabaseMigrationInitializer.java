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
}
