package com.skyfalling.mosika.ui.web.dao;

import com.skyfalling.mosika.ui.web.entity.RuleFlowEntity;
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
 * {@code rule_flow} 表的持久化访问对象
 * <p>
 * 对外查询使用由数据库主键派生的 {@code flowId}，内部关联统一使用整数主键
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Repository
@RequiredArgsConstructor
public class RuleFlowDao {

    /** Spring JDBC 操作入口 */
    private final JdbcTemplate jdbc;

    /** 带命名空间编码的基础查询 */
    private static final String SELECT = "SELECT f.*, n.code AS namespace_code "
            + "FROM rule_flow f JOIN rule_namespace n ON n.id=f.namespace_id";

    /** 数据库行映射器 */
    private static final RowMapper<RuleFlowEntity> MAPPER = (rs, i) -> RuleFlowEntity.builder()
            .id(rs.getLong("id"))
            .namespaceId(rs.getLong("namespace_id"))
            .namespace(rs.getString("namespace_code"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .ruleTree(rs.getString("rule_tree"))
            .status(rs.getInt("status"))
            .version(rs.getLong("version"))
            .createdAt(rs.getString("created_at"))
            .updatedAt(rs.getString("updated_at"))
            .build();

    /** 插入规则流并返回数据库自增主键 */
    public long insert(RuleFlowEntity entity) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rule_flow "
                            + "(namespace_id, name, description, rule_tree, status, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, 0, datetime('now','localtime'), datetime('now','localtime'))",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, entity.getNamespaceId());
            statement.setString(2, entity.getName());
            statement.setString(3, entity.getDescription() == null ? "" : entity.getDescription());
            statement.setString(4, entity.getRuleTree());
            statement.setInt(5, entity.getStatus() == null ? 0 : entity.getStatus());
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to obtain generated id");
        }
        return key.longValue();
    }

    /** 按 flowId 和期望版本更新规则流 */
    public int update(String flowId, RuleFlowEntity entity) {
        return jdbc.update(
                "UPDATE rule_flow SET name=?, description=?, rule_tree=?, status=?, "
                        + "version=version+1, updated_at=datetime('now','localtime') "
                        + "WHERE id=? AND version=?",
                entity.getName(), entity.getDescription() == null ? "" : entity.getDescription(),
                entity.getRuleTree(), entity.getStatus(), RuleIds.parseFlowId(flowId), entity.getVersion());
    }

    /** 仅更新规则流名称和描述 */
    public int updateMeta(String flowId, String name, String description, long version) {
        return jdbc.update(
                "UPDATE rule_flow SET name=?, description=?, version=version+1, "
                        + "updated_at=datetime('now','localtime') WHERE id=? AND version=?",
                name, description == null ? "" : description, RuleIds.parseFlowId(flowId), version);
    }

    /** 按乐观锁停用规则流 */
    public int disable(String flowId, long version) {
        return jdbc.update(
                "UPDATE rule_flow SET status=2, version=version+1, updated_at=datetime('now','localtime') "
                        + "WHERE id=? AND version=?", RuleIds.parseFlowId(flowId), version);
    }

    /** 严格校验前缀并按 flowId 查询规则流 */
    public RuleFlowEntity findByFlowId(String flowId) {
        List<RuleFlowEntity> rows = jdbc.query(
                SELECT + " WHERE f.id=?", MAPPER, RuleIds.parseFlowId(flowId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 分页查询规则流 */
    public List<RuleFlowEntity> list(Integer status, String namespace, String keyword,
                                     long offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, namespace, keyword);
        sql.append(" ORDER BY f.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    /** 统计满足过滤条件的规则流数量 */
    public int count(Integer status, String namespace, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM rule_flow f JOIN rule_namespace n ON n.id=f.namespace_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, namespace, keyword);
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    /** 加载全部生效规则流 */
    public List<RuleFlowEntity> listActive() {
        return jdbc.query(SELECT + " WHERE f.status=1 ORDER BY f.id", MAPPER);
    }

    /** 按命名空间加载全部生效规则流 */
    public List<RuleFlowEntity> listActive(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return listActive();
        }
        return jdbc.query(SELECT + " WHERE f.status=1 AND n.code=? ORDER BY f.id", MAPPER, namespace);
    }

    /** 严格校验前缀并按 flowId 集合批量查询规则流 */
    public List<RuleFlowEntity> findByFlowIds(Collection<String> flowIds) {
        if (flowIds == null || flowIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = flowIds.stream().map(RuleIds::parseFlowId).toList();
        return findByIds(ids);
    }

    /** 按数据库主键集合批量查询规则流，不过滤状态 */
    public List<RuleFlowEntity> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbc.query(SELECT + " WHERE f.id IN (" + placeholders + ")", MAPPER, ids.toArray());
    }

    /** 追加分页过滤条件 */
    private static void appendFilters(StringBuilder sql, List<Object> args, Integer status,
                                      String namespace, String keyword) {
        if (status != null) {
            sql.append(" AND f.status=?");
            args.add(status);
        }
        if (namespace != null && !namespace.isBlank()) {
            sql.append(" AND n.code=?");
            args.add(namespace);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (('f' || f.id) LIKE ? OR f.name LIKE ? OR f.description LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }
}
