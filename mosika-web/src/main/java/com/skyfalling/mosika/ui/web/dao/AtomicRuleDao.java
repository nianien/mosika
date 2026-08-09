package com.skyfalling.mosika.ui.web.dao;

import com.skyfalling.mosika.ui.web.entity.AtomicRuleEntity;
import com.skyfalling.mosika.ui.web.common.RuleIds;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@code atomic_rule} 表的持久化访问对象
 * <p>
 * 对外查询使用由数据库主键派生的 {@code ruleId}，内部关联统一使用整数主键
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Repository
@RequiredArgsConstructor
public class AtomicRuleDao {

    /** Spring JDBC 操作入口 */
    private final JdbcTemplate jdbc;

    /** 带命名空间编码的基础查询 */
    private static final String SELECT = "SELECT a.*, n.code AS namespace_code "
            + "FROM atomic_rule a JOIN rule_namespace n ON n.id=a.namespace_id";

    /** 数据库行映射器 */
    private static final RowMapper<AtomicRuleEntity> MAPPER = (rs, i) -> AtomicRuleEntity.builder()
            .id(rs.getLong("id"))
            .namespaceId(rs.getLong("namespace_id"))
            .namespace(rs.getString("namespace_code"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .expression(rs.getString("expression"))
            .params(rs.getString("params"))
            .kind(rs.getString("kind"))
            .status(rs.getInt("status"))
            .version(rs.getLong("version"))
            .createdAt(rs.getString("created_at"))
            .updatedAt(rs.getString("updated_at"))
            .build();

    /**
     * 插入原子规则
     *
     * @param entity 待插入实体
     * @return 数据库生成的自增主键
     */
    public long insert(AtomicRuleEntity entity) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO atomic_rule "
                            + "(namespace_id, name, description, expression, params, kind, status, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, datetime('now','localtime'), datetime('now','localtime'))",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, entity.getNamespaceId());
            statement.setString(2, entity.getName());
            statement.setString(3, entity.getDescription() == null ? "" : entity.getDescription());
            statement.setString(4, entity.getExpression());
            statement.setString(5, normalizeParams(entity.getParams()));
            statement.setString(6, normalizeKind(entity.getKind()));
            statement.setInt(7, entity.getStatus() == null ? 1 : entity.getStatus());
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to obtain generated id");
        }
        return key.longValue();
    }

    /** 按 ruleId 和期望版本更新规则正文及元数据 */
    public int update(String ruleId, AtomicRuleEntity entity) {
        return jdbc.update(
                "UPDATE atomic_rule SET name=?, description=?, expression=?, params=?, kind=?, status=?, "
                        + "version=version+1, updated_at=datetime('now','localtime') "
                        + "WHERE id=? AND version=?",
                entity.getName(), entity.getDescription() == null ? "" : entity.getDescription(),
                entity.getExpression(), normalizeParams(entity.getParams()),
                normalizeKind(entity.getKind()), entity.getStatus(),
                RuleIds.parseRuleId(ruleId), entity.getVersion());
    }

    /** 按乐观锁停用原子规则 */
    public int disable(String ruleId, long version) {
        return jdbc.update(
                "UPDATE atomic_rule SET status=0, version=version+1, updated_at=datetime('now','localtime') "
                        + "WHERE id=? AND version=?", RuleIds.parseRuleId(ruleId), version);
    }

    /** 按乐观锁启用原子规则 */
    public int enable(String ruleId, long version) {
        return jdbc.update(
                "UPDATE atomic_rule SET status=1, version=version+1, updated_at=datetime('now','localtime') "
                        + "WHERE id=? AND version=?", RuleIds.parseRuleId(ruleId), version);
    }

    /** 严格校验前缀并按 ruleId 查询原子规则 */
    public AtomicRuleEntity findByRuleId(String ruleId) {
        List<AtomicRuleEntity> rows = jdbc.query(
                SELECT + " WHERE a.id=?", MAPPER, RuleIds.parseRuleId(ruleId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 分页查询原子规则 */
    public List<AtomicRuleEntity> list(Integer status, String kind, String namespace,
                                       String keyword, long offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, kind, namespace, keyword);
        sql.append(" ORDER BY a.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    /** 统计满足过滤条件的原子规则数量 */
    public int count(Integer status, String kind, String namespace, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM atomic_rule a JOIN rule_namespace n ON n.id=a.namespace_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, kind, namespace, keyword);
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    /** 加载全部启用原子规则 */
    public List<AtomicRuleEntity> listActive() {
        return jdbc.query(SELECT + " WHERE a.status=1 ORDER BY a.id", MAPPER);
    }

    /** 按命名空间加载启用原子规则 */
    public List<AtomicRuleEntity> listActive(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return listActive();
        }
        return jdbc.query(SELECT + " WHERE a.status=1 AND n.code=? ORDER BY a.id", MAPPER, namespace);
    }

    /** 严格校验前缀并按 ruleId 集合批量查询原子规则 */
    public List<AtomicRuleEntity> findByRuleIds(Collection<String> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = ruleIds.stream().map(RuleIds::parseRuleId).toList();
        return findByIds(ids);
    }

    /** 按数据库主键集合批量查询原子规则，不过滤状态 */
    public List<AtomicRuleEntity> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbc.query(SELECT + " WHERE a.id IN (" + placeholders + ")", MAPPER, ids.toArray());
    }

    /** 追加分页过滤条件 */
    private static void appendFilters(StringBuilder sql, List<Object> args, Integer status,
                                      String kind, String namespace, String keyword) {
        if (status != null) {
            sql.append(" AND a.status=?");
            args.add(status);
        }
        if (kind != null && !kind.isBlank()) {
            sql.append(" AND a.kind=?");
            args.add(kind);
        }
        if (namespace != null && !namespace.isBlank()) {
            sql.append(" AND n.code=?");
            args.add(namespace);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (('r' || a.id) LIKE ? OR a.name LIKE ? OR a.description LIKE ? OR a.expression LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }

    /** 规范化原子规则分类 */
    private static String normalizeKind(String kind) {
        return "action".equals(kind) ? "action" : "condition";
    }

    /** 空参数模板归一为空 JSON 数组，保持列非空约束 */
    private static String normalizeParams(String params) {
        return params == null || params.isBlank() ? "[]" : params;
    }
}
