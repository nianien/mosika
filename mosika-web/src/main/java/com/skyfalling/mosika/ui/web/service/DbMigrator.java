package com.skyfalling.mosika.ui.web.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * 轻量数据库迁移器：对存量 SQLite 库做幂等的增量结构升级。
 * <p>
 * {@code schema.sql} 用 {@code CREATE TABLE IF NOT EXISTS}，无法为已存在的表补列；
 * SQLite 又不支持 {@code ADD COLUMN IF NOT EXISTS}。这里在启动时按 {@code PRAGMA table_info}
 * 检测缺失列并补齐，保证老库平滑升级。
 * <p>
 * 通过被 {@link RuleSuiteManager} 构造依赖，确保其 {@code @PostConstruct} 在 RuleSuite
 * 初次装配（会按新列查询）之前完成。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbMigrator {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    @PostConstruct
    public void migrate() {
        transactionTemplate.executeWithoutResult(status -> {
            addColumnIfMissing("rule_definition", "rule_kind",
                    "ALTER TABLE rule_definition ADD COLUMN rule_kind TEXT NOT NULL DEFAULT 'condition'");
            // 列补齐后再建索引（放这里而非 schema.sql，因 schema.sql 先于 bean 执行、对老库会因缺列报错）。
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_rule_definition_kind ON rule_definition(rule_kind)");
            migrateFlowStatusSemantics();
        });
    }

    /**
     * rule_flow.status 语义升级：旧库 0=停用/1=启用；新语义 0=草稿/1=已生效/2=已停用。
     * 用 {@code PRAGMA user_version} 保证只执行一次，避免把新草稿（也用 status=0）误当停用。
     */
    private void migrateFlowStatusSemantics() {
        Integer v = jdbc.queryForObject("PRAGMA user_version", Integer.class);
        int userVersion = v == null ? 0 : v;
        if (userVersion < 1) {
            int moved = jdbc.update("UPDATE rule_flow SET status=2 WHERE status=0");
            jdbc.execute("PRAGMA user_version = 1");
            log.info("DbMigrator: 迁移 rule_flow 旧停用状态(0→2) 共 {} 行，user_version→1", moved);
        }
    }

    private void addColumnIfMissing(String table, String column, String alterSql) {
        List<Map<String, Object>> cols = jdbc.queryForList("PRAGMA table_info(" + table + ")");
        boolean present = cols.stream().anyMatch(c -> column.equalsIgnoreCase(String.valueOf(c.get("name"))));
        if (present) {
            return;
        }
        jdbc.execute(alterSql);
        log.info("DbMigrator: 已为 {} 增加列 {}", table, column);
    }
}
