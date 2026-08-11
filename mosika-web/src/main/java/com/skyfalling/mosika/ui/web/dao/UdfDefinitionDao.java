package com.skyfalling.mosika.ui.web.dao;

import com.skyfalling.mosika.ui.web.entity.UdfDefinitionEntity;
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
 * {@code udf_definition} 表的持久化访问对象
 * <p>
 * UDF 更新和启停操作使用 {@code version} 字段执行乐观锁控制，完整注册路径由
 * {@code group_name} 和 {@code name} 共同确定
 */
@Repository
@RequiredArgsConstructor
public class UdfDefinitionDao {

    /** Spring JDBC 操作入口 */
    private final JdbcTemplate jdbc;

    /** 带命名空间编码的基础查询 */
    private static final String SELECT = "SELECT u.*, n.code AS namespace_code "
            + "FROM udf_definition u JOIN rule_namespace n ON n.id=u.namespace_id";

    /** 把数据库列映射为 UDF 定义实体的统一行映射器 */
    private static final RowMapper<UdfDefinitionEntity> MAPPER = (rs, i) -> UdfDefinitionEntity.builder()
            .id(rs.getLong("id"))
            .namespaceId(rs.getLong("namespace_id"))
            .namespace(rs.getString("namespace_code"))
            .group(rs.getString("group_name"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .source(rs.getString("source"))
            .status(rs.getInt("status"))
            .version(rs.getLong("version"))
            .createdAt(rs.getString("created_at"))
            .updatedAt(rs.getString("updated_at"))
            .build();

    /**
     * 插入新 UDF 定义并读取数据库生成的自增 ID
     *
     * @param entity 待插入的 UDF 定义
     * @return 数据库生成的 UDF ID
     * @throws IllegalStateException JDBC 驱动没有返回生成主键时抛出
     */
    public long insert(UdfDefinitionEntity entity) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO udf_definition "
                            + "(namespace_id, group_name, name, description, source, status, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 0, datetime('now','localtime'), datetime('now','localtime'))",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, entity.getNamespaceId());
            statement.setString(2, entity.getGroup());
            statement.setString(3, entity.getName());
            statement.setString(4, entity.getDescription() == null ? "" : entity.getDescription());
            statement.setString(5, entity.getSource());
            statement.setInt(6, entity.getStatus() == null ? 1 : entity.getStatus());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to obtain generated id");
        }
        return key.longValue();
    }

    /**
     * 按 ID 和期望版本号更新 UDF 路径、描述与源码
     *
     * @param entity 包含数据库 ID、期望版本号和新字段值的 UDF 定义
     * @return 更新成功时返回 1，记录不存在或版本冲突时返回 0
     */
    public int update(UdfDefinitionEntity entity) {
        return jdbc.update(
                "UPDATE udf_definition SET group_name=?, name=?, description=?, source=?, " +
                        "version=version+1, updated_at=datetime('now','localtime') WHERE id=? AND version=?",
                entity.getGroup(), entity.getName(),
                entity.getDescription() == null ? "" : entity.getDescription(), entity.getSource(),
                entity.getId(), entity.getVersion());
    }

    /**
     * 按乐观锁把 UDF 状态更新为停用
     *
     * @param id      UDF 数据库 ID
     * @param version 客户端持有的期望版本号
     * @return 更新成功时返回 1，记录不存在或版本冲突时返回 0
     */
    public int disable(long id, long version) {
        return jdbc.update(
                "UPDATE udf_definition SET status=0, version=version+1, updated_at=datetime('now','localtime') " +
                        "WHERE id=? AND version=?", id, version);
    }

    /**
     * 按乐观锁把 UDF 状态更新为启用
     *
     * @param id      UDF 数据库 ID
     * @param version 客户端持有的期望版本号
     * @return 更新成功时返回 1，记录不存在或版本冲突时返回 0
     */
    public int enable(long id, long version) {
        return jdbc.update(
                "UPDATE udf_definition SET status=1, version=version+1, updated_at=datetime('now','localtime') " +
                        "WHERE id=? AND version=?", id, version);
    }

    /**
     * 按数据库 ID 查询 UDF 定义
     *
     * @param id UDF 数据库 ID
     * @return UDF 定义，不存在时返回 {@code null}
     */
    public UdfDefinitionEntity findById(long id) {
        List<UdfDefinitionEntity> rows = jdbc.query(SELECT + " WHERE u.id=?", MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 按完整注册路径查询 UDF 定义
     *
     * @param namespace 规则命名空间
     * @param group     点分隔 UDF 分组
     * @param name      函数名称
     * @return 占用该路径的 UDF 定义，不存在时返回 {@code null}
     */
    public UdfDefinitionEntity findByPath(String namespace, String group, String name) {
        List<UdfDefinitionEntity> rows = jdbc.query(
                SELECT + " WHERE n.code=? AND u.group_name=? AND u.name=?",
                MAPPER, namespace, group, name);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 按可选条件分页查询 UDF 定义
     *
     * @param status  启停状态，传 {@code null} 表示不过滤
     * @param namespace 规则命名空间，传空值表示不过滤
     * @param keyword   UDF 分组、名称、描述或源码关键字，传空值表示不过滤
     * @param offset    从零开始的结果偏移量
     * @param limit     最大返回条数
     * @return 按命名空间和函数名称升序排列的 UDF 定义列表
     */
    public List<UdfDefinitionEntity> list(Integer status, String namespace, String keyword,
                                          long offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, namespace, keyword);
        sql.append(" ORDER BY u.group_name ASC, u.name ASC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    /**
     * 统计满足可选过滤条件的 UDF 定义数量
     *
     * @param status  启停状态，传 {@code null} 表示不过滤
     * @param namespace 规则命名空间，传空值表示不过滤
     * @param keyword   UDF 分组、名称、描述或源码关键字，传空值表示不过滤
     * @return 匹配记录数量
     */
    public int count(Integer status, String namespace, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM udf_definition u "
                        + "JOIN rule_namespace n ON n.id=u.namespace_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, status, namespace, keyword);
        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    /**
     * 加载全部启用 UDF，用于装配运行态规则套件
     *
     * @return 按 ID 升序排列的启用 UDF 列表
     */
    public List<UdfDefinitionEntity> listActive() {
        return jdbc.query(SELECT + " WHERE u.status=1 ORDER BY u.id ASC", MAPPER);
    }

    /** 按命名空间加载全部启用 UDF */
    public List<UdfDefinitionEntity> listActive(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return listActive();
        }
        return jdbc.query(
                SELECT + " WHERE u.status=1 AND n.code=? ORDER BY u.id ASC",
                MAPPER, namespace);
    }

    /**
     * 向查询 SQL 和位置参数列表追加可选过滤条件
     *
     * @param sql     待追加条件的 SQL
     * @param args    与 SQL 占位符顺序一致的位置参数
     * @param status  启停状态
     * @param namespace 规则命名空间
     * @param keyword   模糊查询关键字
     */
    private static void appendFilters(StringBuilder sql, List<Object> args, Integer status,
                                      String namespace, String keyword) {
        if (status != null) {
            sql.append(" AND u.status=?");
            args.add(status);
        }
        if (namespace != null && !namespace.isBlank()) {
            sql.append(" AND n.code=?");
            args.add(namespace);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (u.group_name LIKE ? OR u.name LIKE ? OR u.description LIKE ? OR u.source LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
    }
}
