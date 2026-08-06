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
}
