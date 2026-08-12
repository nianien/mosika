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
 * 对外查询使用由 {@code flow_key} 派生的稳定 {@code flowId}，引用表关联版本记录主键
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
            .flowKey(rs.getLong("flow_key"))
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

    /** 插入场景的首个草稿版本并返回版本记录主键 */
    public long insertInitial(RuleFlowEntity entity) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rule_flow "
                            + "(flow_key, namespace_id, name, description, rule_tree, status, version, "
                            + "created_at, updated_at) "
                            + "VALUES ((SELECT COALESCE(MAX(flow_key),0)+1 FROM rule_flow), "
                            + "?, ?, ?, ?, ?, 1, datetime('now','localtime'), datetime('now','localtime'))",
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

    /** 复制指定版本并插入新的草稿版本 */
    public long insertVersion(long flowKey, RuleFlowEntity entity) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rule_flow "
                            + "(flow_key, namespace_id, name, description, rule_tree, status, version, "
                            + "created_at, updated_at) SELECT ?, ?, ?, ?, ?, 0, "
                            + "COALESCE(MAX(version),0)+1, datetime('now','localtime'), "
                            + "datetime('now','localtime') FROM rule_flow WHERE flow_key=?",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, flowKey);
            statement.setLong(2, entity.getNamespaceId());
            statement.setString(3, entity.getName());
            statement.setString(4, entity.getDescription() == null ? "" : entity.getDescription());
            statement.setString(5, entity.getRuleTree());
            statement.setLong(6, flowKey);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to obtain generated id");
        }
        return key.longValue();
    }

    /** 仅更新指定草稿版本 */
    public int updateDraft(String flowId, RuleFlowEntity entity) {
        return jdbc.update(
                "UPDATE rule_flow SET name=?, description=?, rule_tree=?, "
                        + "updated_at=datetime('now','localtime') "
                        + "WHERE flow_key=? AND version=? AND status=0",
                entity.getName(), entity.getDescription() == null ? "" : entity.getDescription(),
                entity.getRuleTree(), RuleIds.parseFlowId(flowId), entity.getVersion());
    }

    /** 仅更新指定草稿版本的名称和描述 */
    public int updateMeta(String flowId, String name, String description, long version) {
        return jdbc.update(
                "UPDATE rule_flow SET name=?, description=?, updated_at=datetime('now','localtime') "
                        + "WHERE flow_key=? AND version=? AND status=0",
                name, description == null ? "" : description, RuleIds.parseFlowId(flowId), version);
    }

    /** 停用指定的当前发布版本 */
    public int disable(String flowId, long version) {
        return jdbc.update(
                "UPDATE rule_flow SET status=2, updated_at=datetime('now','localtime') "
                        + "WHERE flow_key=? AND version=? AND status=1", RuleIds.parseFlowId(flowId), version);
    }

    /** 按 flowId 查询默认编辑版本：最新草稿优先，其次当前发布版本 */
    public RuleFlowEntity findByFlowId(String flowId) {
        List<RuleFlowEntity> rows = jdbc.query(
                SELECT + " WHERE f.flow_key=? "
                        + "ORDER BY CASE f.status WHEN 0 THEN 0 WHEN 1 THEN 1 ELSE 2 END, f.version DESC LIMIT 1",
                MAPPER, RuleIds.parseFlowId(flowId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 按 flowId 和业务版本查询场景版本 */
    public RuleFlowEntity findByVersion(String flowId, long version) {
        List<RuleFlowEntity> rows = jdbc.query(
                SELECT + " WHERE f.flow_key=? AND f.version=?",
                MAPPER, RuleIds.parseFlowId(flowId), version);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 按版本记录主键查询场景版本 */
    public RuleFlowEntity findById(long id) {
        List<RuleFlowEntity> rows = jdbc.query(SELECT + " WHERE f.id=?", MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查询指定场景的当前发布版本 */
    public RuleFlowEntity findActiveByFlowId(String flowId) {
        List<RuleFlowEntity> rows = jdbc.query(
                SELECT + " WHERE f.flow_key=? AND f.status=1 LIMIT 1",
                MAPPER, RuleIds.parseFlowId(flowId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查询指定场景的全部版本 */
    public List<RuleFlowEntity> listVersions(String flowId) {
        return jdbc.query(SELECT + " WHERE f.flow_key=? ORDER BY f.version DESC",
                MAPPER, RuleIds.parseFlowId(flowId));
    }

    /** 将当前发布版本转为历史版本 */
    public int historizeActive(long flowKey) {
        return jdbc.update(
                "UPDATE rule_flow SET status=3, updated_at=datetime('now','localtime') "
                        + "WHERE flow_key=? AND status=1", flowKey);
    }

    /** 发布指定草稿版本并同步本次提交内容 */
    public int publishDraft(String flowId, RuleFlowEntity entity) {
        return jdbc.update(
                "UPDATE rule_flow SET name=?, description=?, rule_tree=?, status=1, "
                        + "updated_at=datetime('now','localtime') "
                        + "WHERE flow_key=? AND version=? AND status=0",
                entity.getName(), entity.getDescription() == null ? "" : entity.getDescription(),
                entity.getRuleTree(), RuleIds.parseFlowId(flowId), entity.getVersion());
    }

    /** 分页查询业务场景，每个场景只返回一个默认编辑版本 */
    public List<RuleFlowEntity> list(Integer status, String namespace, String keyword,
                                     long offset, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT);
        appendRepresentativeVersion(sql, args, status);
        appendFilters(sql, args, namespace, keyword);
        sql.append(" ORDER BY f.flow_key DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    /** 统计满足过滤条件的业务场景数量 */
    public int count(Integer status, String namespace, String keyword) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM rule_flow f JOIN rule_namespace n ON n.id=f.namespace_id");
        appendRepresentativeVersion(sql, args, status);
        appendFilters(sql, args, namespace, keyword);
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

    /** 严格校验前缀并按 flowId 集合批量查询当前发布版本 */
    public List<RuleFlowEntity> findByFlowIds(Collection<String> flowIds) {
        if (flowIds == null || flowIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> flowKeys = flowIds.stream().map(RuleIds::parseFlowId).toList();
        String placeholders = String.join(",", Collections.nCopies(flowKeys.size(), "?"));
        return jdbc.query(SELECT + " WHERE f.status=1 AND f.flow_key IN (" + placeholders + ")",
                MAPPER, flowKeys.toArray());
    }

    /** 按数据库主键集合批量查询规则流，不过滤状态 */
    public List<RuleFlowEntity> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbc.query(SELECT + " WHERE f.id IN (" + placeholders + ")", MAPPER, ids.toArray());
    }

    /** 选择每个业务场景在当前筛选条件下展示的版本 */
    private static void appendRepresentativeVersion(StringBuilder sql, List<Object> args, Integer status) {
        sql.append(" WHERE f.id=(SELECT v.id FROM rule_flow v WHERE v.flow_key=f.flow_key");
        if (status != null) {
            sql.append(" AND v.status=?");
            args.add(status);
        }
        sql.append(" ORDER BY CASE v.status WHEN 0 THEN 0 WHEN 1 THEN 1 ELSE 2 END, "
                + "v.version DESC LIMIT 1)");
    }

    /** 追加分页过滤条件 */
    private static void appendFilters(StringBuilder sql, List<Object> args,
                                      String namespace, String keyword) {
        if (namespace != null && !namespace.isBlank()) {
            sql.append(" AND n.code=?");
            args.add(namespace);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (('f' || f.flow_key) LIKE ? OR f.name LIKE ? OR f.description LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }
}
