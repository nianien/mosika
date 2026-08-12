package com.skyfalling.mosika.ui.web.dao;

import com.skyfalling.mosika.ui.web.common.RuleIds;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 维护规则流到原子规则、规则流到规则流的内部引用索引
 * <p>
 * 引用表只保存两张实体表的整数主键，对外 ID 只在 API 和规则树边界使用
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Repository
@RequiredArgsConstructor
public class FlowReferenceDao {

    /** Spring JDBC 操作入口 */
    private final JdbcTemplate jdbc;

    /** 用指定引用集合完全覆盖一个规则流的派生引用 */
    public void replaceForFlow(long flowId, Collection<Long> ruleIds,
                               Collection<Long> referencedFlowIds) {
        jdbc.update("DELETE FROM flow_atomic_ref WHERE flow_id=?", flowId);
        jdbc.update("DELETE FROM flow_flow_ref WHERE flow_id=?", flowId);
        batchInsert("INSERT OR IGNORE INTO flow_atomic_ref (flow_id, rule_id) VALUES (?, ?)",
                flowId, ruleIds);
        batchInsert("INSERT OR IGNORE INTO flow_flow_ref "
                        + "(flow_id, referenced_flow_id) VALUES (?, ?)",
                flowId, referencedFlowIds);
    }

    /** 当前发布版本切换后，把运行态调用方的引用指向新版本记录 */
    public void retargetActiveReferences(long flowKey, long currentFlowId) {
        jdbc.update(
                "INSERT OR IGNORE INTO flow_flow_ref (flow_id, referenced_flow_id) "
                        + "SELECT r.flow_id, ? FROM flow_flow_ref r "
                        + "JOIN rule_flow f ON f.id=r.flow_id "
                        + "WHERE r.referenced_flow_id IN "
                        + "(SELECT id FROM rule_flow WHERE flow_key=? AND id<>?) AND f.status=1",
                currentFlowId, flowKey, currentFlowId);
        jdbc.update(
                "DELETE FROM flow_flow_ref WHERE referenced_flow_id IN "
                        + "(SELECT id FROM rule_flow WHERE flow_key=? AND id<>?) "
                        + "AND flow_id IN (SELECT id FROM rule_flow WHERE status=1)",
                flowKey, currentFlowId);
    }

    /** 按命名空间统计每条原子规则被运行态规则流闭包直接引用的次数 */
    public Map<String, Integer> atomicRefCountsByActiveFlow(long namespaceId) {
        Map<String, Integer> counts = new HashMap<>();
        jdbc.query(
                "WITH RECURSIVE runtime(id) AS ("
                        + "SELECT id FROM rule_flow WHERE status=1 AND namespace_id=? "
                        + "UNION SELECT r.referenced_flow_id FROM flow_flow_ref r "
                        + "JOIN runtime p ON p.id=r.flow_id"
                        + ") SELECT r.rule_id, COUNT(*) AS c FROM flow_atomic_ref r "
                        + "JOIN runtime f ON f.id=r.flow_id GROUP BY r.rule_id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        counts.put(RuleIds.ruleId(rs.getLong("rule_id")), rs.getInt("c")),
                namespaceId);
        return counts;
    }

    /** 查询生效根规则流及其递归引用的全部规则流数据库主键 */
    public Set<Long> runtimeFlowIds() {
        return new HashSet<>(jdbc.query(
                "WITH RECURSIVE runtime(id) AS ("
                        + "SELECT id FROM rule_flow WHERE status=1 "
                        + "UNION "
                        + "SELECT r.referenced_flow_id FROM flow_flow_ref r "
                        + "JOIN runtime p ON p.id=r.flow_id"
                        + ") SELECT id FROM runtime",
                (rs, i) -> rs.getLong(1)));
    }

    /** 按命名空间查询生效根规则流及其递归引用的全部规则流数据库主键 */
    public Set<Long> runtimeFlowIds(long namespaceId) {
        return new HashSet<>(jdbc.query(
                "WITH RECURSIVE runtime(id) AS ("
                        + "SELECT id FROM rule_flow WHERE status=1 AND namespace_id=? "
                        + "UNION "
                        + "SELECT r.referenced_flow_id FROM flow_flow_ref r "
                        + "JOIN runtime p ON p.id=r.flow_id"
                        + ") SELECT id FROM runtime",
                (rs, i) -> rs.getLong(1),
                namespaceId));
    }

    /** 查询运行态规则流闭包直接或间接依赖的全部原子规则数据库主键 */
    public Set<Long> runtimeRuleIds() {
        return new HashSet<>(jdbc.query(
                "WITH RECURSIVE runtime(id) AS ("
                        + "SELECT id FROM rule_flow WHERE status=1 "
                        + "UNION "
                        + "SELECT r.referenced_flow_id FROM flow_flow_ref r "
                        + "JOIN runtime p ON p.id=r.flow_id"
                        + ") SELECT DISTINCT a.rule_id FROM flow_atomic_ref a "
                        + "JOIN runtime f ON f.id=a.flow_id",
                (rs, i) -> rs.getLong(1)));
    }

    /** 按命名空间查询运行态规则流闭包依赖的全部原子规则数据库主键 */
    public Set<Long> runtimeRuleIds(long namespaceId) {
        return new HashSet<>(jdbc.query(
                "WITH RECURSIVE runtime(id) AS ("
                        + "SELECT id FROM rule_flow WHERE status=1 AND namespace_id=? "
                        + "UNION "
                        + "SELECT r.referenced_flow_id FROM flow_flow_ref r "
                        + "JOIN runtime p ON p.id=r.flow_id"
                        + ") SELECT DISTINCT a.rule_id FROM flow_atomic_ref a "
                        + "JOIN runtime f ON f.id=a.flow_id",
                (rs, i) -> rs.getLong(1),
                namespaceId));
    }

    /** 批量写入同一来源规则流的引用 */
    private void batchInsert(String sql, long flowId, Collection<Long> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>(targets.size());
        for (Long target : targets) {
            batch.add(new Object[]{flowId, target});
        }
        jdbc.batchUpdate(sql, batch);
    }
}
