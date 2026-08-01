package com.skyfalling.mousika.ui.web.dao;

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
 * {@code flow_rule_ref} 派生边表：记录 flow 引用了哪些 rule。
 * <p>
 * 每次 rule_flow 写入或更新时，由 Service 层用 {@code TreeNode.collect()}
 * 重新计算引用集合，然后 {@link #replaceForFlow(long, Collection)} 覆盖写入。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Repository
@RequiredArgsConstructor
public class FlowRuleRefDao {

    private final JdbcTemplate jdbc;

    /** 用指定 rule 集合完全覆盖某 flow 的引用行。 */
    public void replaceForFlow(long flowId, Collection<Long> ruleIds) {
        jdbc.update("DELETE FROM flow_rule_ref WHERE flow_id=?", flowId);
        if (ruleIds == null || ruleIds.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>(ruleIds.size());
        for (Long rid : ruleIds) {
            batch.add(new Object[]{flowId, rid});
        }
        jdbc.batchUpdate("INSERT OR IGNORE INTO flow_rule_ref (flow_id, rule_id) VALUES (?, ?)", batch);
    }

    /** 反查：某条 rule 目前被哪些启用中的 flow 引用。 */
    public Set<Long> activeFlowsReferencing(long ruleId) {
        List<Long> ids = jdbc.query(
                "SELECT r.flow_id FROM flow_rule_ref r " +
                        "JOIN rule_flow f ON f.id = r.flow_id " +
                        "WHERE r.rule_id=? AND f.status=1",
                (rs, i) -> rs.getLong(1),
                ruleId);
        return new HashSet<>(ids);
    }

    /**
     * 全量统计：每条 rule 被多少个“已生效(status=1)”的 flow 引用。
     * 供原子规则库列表直接取引用数，避免下载并解析全部流程树。
     */
    public Map<Long, Integer> refCountsByActiveFlow() {
        Map<Long, Integer> counts = new HashMap<>();
        jdbc.query(
                "SELECT r.rule_id, COUNT(*) AS c FROM flow_rule_ref r " +
                        "JOIN rule_flow f ON f.id = r.flow_id " +
                        "WHERE f.status=1 GROUP BY r.rule_id",
                (rs, i) -> {
                    counts.put(rs.getLong("rule_id"), rs.getInt("c"));
                    return null;
                });
        return counts;
    }
}
