package com.skyfalling.mousika.ui.web.dao;

import com.skyfalling.mousika.ui.web.entity.RuleFlowEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code rule_flow} 表的持久化访问对象。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Repository
@RequiredArgsConstructor
public class RuleFlowDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<RuleFlowEntity> MAPPER = (rs, i) -> RuleFlowEntity.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .ruleTree(rs.getString("rule_tree"))
            .status(rs.getInt("status"))
            .version(rs.getLong("version"))
            .createdAt(rs.getString("created_at"))
            .updatedAt(rs.getString("updated_at"))
            .build();

    public long insert(RuleFlowEntity e) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rule_flow (name, description, rule_tree, status, version, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 0, datetime('now','localtime'), datetime('now','localtime'))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, e.getName());
            ps.setString(2, e.getDescription() == null ? "" : e.getDescription());
            ps.setString(3, e.getRuleTree());
            ps.setInt(4, e.getStatus() == null ? 0 : e.getStatus());
            return ps;
        }, keyHolder);
        Number k = keyHolder.getKey();
        if (k == null) {
            throw new IllegalStateException("failed to obtain generated id");
        }
        return k.longValue();
    }

    public int update(RuleFlowEntity e) {
        return jdbc.update(
                "UPDATE rule_flow " +
                        "SET name=?, description=?, rule_tree=?, status=?, " +
                        "    version=version+1, updated_at=datetime('now','localtime') " +
                        "WHERE id=? AND version=?",
                e.getName(),
                e.getDescription() == null ? "" : e.getDescription(),
                e.getRuleTree(),
                e.getStatus() == null ? 1 : e.getStatus(),
                e.getId(),
                e.getVersion());
    }

    /** 仅更新名称/描述（不动 rule_tree 与 status），供详情抽屉的元数据编辑使用。 */
    public int updateMeta(long id, String name, String description, long version) {
        return jdbc.update(
                "UPDATE rule_flow SET name=?, description=?, version=version+1, updated_at=datetime('now','localtime') " +
                        "WHERE id=? AND version=?",
                name,
                description == null ? "" : description,
                id,
                version);
    }

    /** 停用（新语义 status=2；0 现表示草稿）。 */
    public int disable(long id, long version) {
        return jdbc.update(
                "UPDATE rule_flow SET status=2, version=version+1, updated_at=datetime('now','localtime') " +
                        "WHERE id=? AND version=?",
                id, version);
    }

    public RuleFlowEntity findById(long id) {
        List<RuleFlowEntity> list = jdbc.query(
                "SELECT * FROM rule_flow WHERE id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<RuleFlowEntity> list(Integer status, String keyword, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM rule_flow WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND status=?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (name LIKE ? OR description LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public int count(Integer status, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM rule_flow WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND status=?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (name LIKE ? OR description LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
        }
        Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<RuleFlowEntity> listActive() {
        return jdbc.query(
                "SELECT * FROM rule_flow WHERE status=1 ORDER BY id ASC",
                MAPPER);
    }
}
