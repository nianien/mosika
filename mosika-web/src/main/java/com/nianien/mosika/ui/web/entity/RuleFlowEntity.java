package com.nianien.mosika.ui.web.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nianien.mosika.ui.web.common.RuleIds;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code rule_flow} 表的实体映射
 * <p>
 * {@code id} 是版本记录主键，{@code flowKey} 是同一业务场景跨版本稳定不变的标识，
 * 进入 UI 树、REST API 和 Core 的 {@code flowId} 由 {@code f + flowKey} 实时派生
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleFlowEntity {

    /** 数据库自增主键 */
    @JsonIgnore
    private Long id;

    /** 同一业务场景跨版本稳定不变的数据库标识 */
    @JsonIgnore
    private Long flowKey;

    /** 所属命名空间数据库主键 */
    @JsonIgnore
    private Long namespaceId;

    /** 所属命名空间业务编码 */
    private String namespace;

    /** 面向用户展示的规则流名称 */
    private String name;

    /** 规则流业务描述 */
    private String description;

    /** UI AST JSON */
    private String ruleTree;

    /** 0 草稿；1 当前发布；2 已停用；3 历史版本 */
    private Integer status;

    /** 场景内递增的业务版本号 */
    private Long version;

    /** SQLite 记录创建时间字符串 */
    private String createdAt;

    /** SQLite 记录最后更新时间字符串 */
    private String updatedAt;

    /** 列表接口摘要：规则树节点总数，不落库 */
    private Integer nodeCount;

    /** 列表接口摘要：规则树中的全部业务引用，不落库 */
    private List<String> referencedRuleIds;

    /** 返回由业务场景标识派生的规则流 ID */
    @JsonProperty(value = "flowId", access = JsonProperty.Access.READ_ONLY)
    public String getFlowId() {
        return RuleIds.flowId(flowKey);
    }
}
