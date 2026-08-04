package com.skyfalling.mosika.ui.web.dao;

import com.skyfalling.mosika.ui.web.entity.RuleDefinitionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * {@code rule_definition} 表的持久化访问对象。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Repository
@RequiredArgsConstructor
public class RuleDefinitionDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<RuleDefinitionEntity> MAPPER = (rs, i) -> RuleDefinitionEntity.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .expression(rs.getString("expression"))
            .useType(rs.getInt("use_type"))
            .ruleKind(rs.getString("rule_kind"))
            .status(rs.getInt("status"))
            .version(rs.getLong("version"))
            .createdAt(rs.getString("created_at"))
            .updatedAt(rs.getString("updated_at"))
            .build();

    private static String normalizeKind(String kind) {
        return "action".equals(kind) ? "action" : "condition";
    }

    /**
     * 插入新记录并回填自增 id。
     */
    public long insert(RuleDefinitionEntity e) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rule_definition (name, description, expression, use_type, rule_kind, status, version, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 0, datetime('now','localtime'), datetime('now','localtime'))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, e.getName());
            ps.setString(2, e.getDescription() == null ? "" : e.getDescription());
            ps.setString(3, e.getExpression());
            ps.setInt(4, e.getUseType() == null ? 0 : e.getUseType());
            ps.setString(5, normalizeKind(e.getRuleKind()));
            ps.setInt(6, e.getStatus() == null ? 1 : e.getStatus());
            return ps;
        }, keyHolder);
        Number k = keyHolder.getKey();
        if (k == null) {
            throw new IllegalStateException("failed to obtain generated id");
        }
        return k.longValue();
    }

    /**
     * 乐观锁更新；返回受影响行数。
     */
    public int update(RuleDefinitionEntity e) {
        return jdbc.update(
                "UPDATE rule_definition " +
                        "SET name=?, description=?, expression=?, use_type=?, rule_kind=?, status=?, " +
                        "    version=version+1, updated_at=datetime('now','localtime') " +
                        "WHERE id=? AND version=?",
                e.getName(),
                e.getDescription() == null ? "" : e.getDescription(),
                e.getExpression(),
                e.getUseType() == null ? 0 : e.getUseType(),
                normalizeKind(e.getRuleKind()),
                e.getStatus() == null ? 1 : e.getStatus(),
                e.getId(),
                e.getVersion());
    }

    /**
     * 逻辑删除（下线）。
     */
    public int disable(long id, long version) {
        return jdbc.update(
                "UPDATE rule_definition SET status=0, version=version+1, updated_at=datetime('now','localtime') " +
                        "WHERE id=? AND version=?",
                id, version);
    }

    /**
     * 重新启用。
     */
    public int enable(long id, long version) {
        return jdbc.update(
                "UPDATE rule_definition SET status=1, version=version+1, updated_at=datetime('now','localtime') " +
                        "WHERE id=? AND version=?",
                id, version);
    }

    public RuleDefinitionEntity findById(long id) {
        List<RuleDefinitionEntity> list = jdbc.query(
                "SELECT * FROM rule_definition WHERE id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 分页查询。
     *
     * @param status  为 null 时不过滤
     * @param useType 为 null 时不过滤
     */
    public List<RuleDefinitionEntity> list(Integer status, Integer useType, String ruleKind, String keyword,
                                           long offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM rule_definition WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        appendFilters(sql, args, status, useType, ruleKind, keyword);
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public int count(Integer status, Integer useType, String ruleKind, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM rule_definition WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        appendFilters(sql, args, status, useType, ruleKind, keyword);
        Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return n == null ? 0 : n;
    }

    private static void appendFilters(StringBuilder sql, java.util.List<Object> args,
                                      Integer status, Integer useType, String ruleKind, String keyword) {
        if (status != null) {
            sql.append(" AND status=?");
            args.add(status);
        }
        if (useType != null) {
            sql.append(" AND use_type=?");
            args.add(useType);
        }
        if (ruleKind != null && !ruleKind.isBlank()) {
            sql.append(" AND rule_kind=?");
            args.add(ruleKind);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (name LIKE ? OR description LIKE ? OR expression LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }

    /**
     * 加载所有启用规则，用于装配 RuleSuite。
     */
    public List<RuleDefinitionEntity> listActive() {
        return jdbc.query(
                "SELECT * FROM rule_definition WHERE status=1 ORDER BY id ASC",
                MAPPER);
    }

    /**
     * 按 id 集合批量查询（不过滤 status）。
     */
    public List<RuleDefinitionEntity> findByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbc.query(
                "SELECT * FROM rule_definition WHERE id IN (" + placeholders + ")",
                MAPPER, ids.toArray());
    }
}
