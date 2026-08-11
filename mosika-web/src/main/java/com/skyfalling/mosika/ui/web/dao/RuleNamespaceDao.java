package com.skyfalling.mosika.ui.web.dao;

import com.skyfalling.mosika.ui.web.entity.RuleNamespaceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/** {@code rule_namespace} 表的持久化访问对象 */
@Repository
@RequiredArgsConstructor
public class RuleNamespaceDao {

    /** Spring JDBC 操作入口 */
    private final JdbcTemplate jdbc;

    /** 数据库行映射器 */
    private static final RowMapper<RuleNamespaceEntity> MAPPER = (rs, i) -> RuleNamespaceEntity.builder()
            .id(rs.getLong("id"))
            .code(rs.getString("code"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .status(rs.getInt("status"))
            .createdAt(rs.getString("created_at"))
            .updatedAt(rs.getString("updated_at"))
            .build();

    /** 按业务编码查询命名空间 */
    public RuleNamespaceEntity findByCode(String code) {
        List<RuleNamespaceEntity> rows = jdbc.query(
                "SELECT * FROM rule_namespace WHERE code=?", MAPPER, code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查询全部命名空间 */
    public List<RuleNamespaceEntity> list() {
        return jdbc.query("SELECT * FROM rule_namespace ORDER BY id", MAPPER);
    }

    /** 创建命名空间 */
    public void insert(RuleNamespaceEntity entity) throws DuplicateKeyException {
        jdbc.update(
                "INSERT INTO rule_namespace (code, name, description, status) VALUES (?, ?, ?, 1)",
                entity.getCode(), entity.getName(),
                entity.getDescription() == null ? "" : entity.getDescription());
    }

    /** 更新命名空间名称和说明 */
    public int update(String code, String name, String description) {
        return jdbc.update(
                "UPDATE rule_namespace SET name=?, description=?, "
                        + "updated_at=datetime('now','localtime') WHERE code=?",
                name, description == null ? "" : description, code);
    }

    /** 更新命名空间启停状态 */
    public int updateStatus(String code, int status) {
        return jdbc.update(
                "UPDATE rule_namespace SET status=?, updated_at=datetime('now','localtime') WHERE code=?",
                status, code);
    }

    /** 统计命名空间下的规则、规则流和 UDF 数量 */
    public Usage countUsage(long namespaceId) {
        return jdbc.queryForObject(
                "SELECT "
                        + "(SELECT COUNT(*) FROM atomic_rule WHERE namespace_id=?) AS rule_count, "
                        + "(SELECT COUNT(*) FROM rule_flow WHERE namespace_id=?) AS flow_count, "
                        + "(SELECT COUNT(*) FROM udf_definition WHERE namespace_id=?) AS udf_count",
                (rs, i) -> new Usage(
                        rs.getInt("rule_count"),
                        rs.getInt("flow_count"),
                        rs.getInt("udf_count")),
                namespaceId, namespaceId, namespaceId);
    }

    /** 命名空间内容使用量 */
    public record Usage(int ruleCount, int flowCount, int udfCount) {
    }
}
