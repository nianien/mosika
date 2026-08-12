package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.ui.tree.node.TreeNode;
import com.skyfalling.mosika.ui.web.common.BusinessException;
import com.skyfalling.mosika.ui.web.common.RuleIds;
import com.skyfalling.mosika.ui.web.dao.AtomicRuleDao;
import com.skyfalling.mosika.ui.web.dao.FlowReferenceDao;
import com.skyfalling.mosika.ui.web.dao.RuleFlowDao;
import com.skyfalling.mosika.ui.web.dao.RuleNamespaceDao;
import com.skyfalling.mosika.ui.web.entity.AtomicRuleEntity;
import com.skyfalling.mosika.ui.web.entity.RuleFlowEntity;
import com.skyfalling.mosika.ui.web.entity.RuleNamespaceEntity;
import com.skyfalling.mosika.ui.web.service.RuleTreeCompiler.CompileResult;
import com.skyfalling.mosika.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 规则流业务服务
 * <p>
 * 负责规则树规范化、引用类型和命名空间校验、引用索引重建以及全局 RuleSuite 刷新
 * 命名空间约束只存在于本服务，不传入 Core
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Service
@RequiredArgsConstructor
public class RuleFlowService {

    /** 草稿状态 */
    public static final int DRAFT = 0;

    /** 当前发布状态 */
    public static final int PUBLISHED = 1;

    /** 已停用状态 */
    public static final int DISABLED = 2;

    /** 历史版本状态 */
    public static final int HISTORICAL = 3;

    /** 条件位置允许的内置引用 */
    private static final Set<String> CONDITION_BUILTINS = Set.of(
            Constants.TRUE, Constants.FALSE, Constants.NULL);

    /** 动作位置允许的内置引用 */
    private static final Set<String> ACTION_BUILTINS = Set.of(Constants.NOP);

    /** 规则流持久化访问对象 */
    private final RuleFlowDao flowDao;

    /** 原子规则持久化访问对象 */
    private final AtomicRuleDao atomicRuleDao;

    /** 两类规则流引用索引 */
    private final FlowReferenceDao referenceDao;

    /** 命名空间持久化访问对象 */
    private final RuleNamespaceDao namespaceDao;

    /** 全局运行态规则套件管理器 */
    private final RuleSuiteManager suiteManager;

    /** 新建规则流并始终保存为草稿 */
    @Transactional
    public RuleFlowEntity create(RuleFlowEntity request) {
        basicCheck(request);
        RuleNamespaceEntity namespace = requireNamespace(request.getNamespace());
        request.setNamespaceId(namespace.getId());
        request.setNamespace(namespace.getCode());
        request.setRuleTree(RuleTreeCompiler.canonicalizeLenient(request.getRuleTree()));
        request.setStatus(DRAFT);
        request.setVersion(1L);
        long id = flowDao.insertInitial(request);
        referenceDao.replaceForFlow(id, Set.of(), Set.of());
        return flowDao.findById(id);
    }

    /** 基于指定版本创建新的草稿版本 */
    @Transactional
    public RuleFlowEntity createVersion(String flowId, long baseVersion) {
        RuleFlowEntity base = requireFlowVersion(flowId, baseVersion);
        RuleFlowEntity draft = RuleFlowEntity.builder()
                .flowKey(base.getFlowKey())
                .namespaceId(base.getNamespaceId())
                .namespace(base.getNamespace())
                .name(base.getName())
                .description(base.getDescription())
                .ruleTree(base.getRuleTree())
                .status(DRAFT)
                .build();
        long id = flowDao.insertVersion(base.getFlowKey(), draft);
        referenceDao.replaceForFlow(id, Set.of(), Set.of());
        return flowDao.findById(id);
    }

    /** 保存指定草稿版本，业务版本号保持不变 */
    @Transactional
    public RuleFlowEntity saveDraft(String flowId, RuleFlowEntity request) {
        requireVersion(request, "update");
        RuleFlowEntity existing = requireFlowVersion(flowId, request.getVersion());
        requireDraft(existing);
        basicCheck(request);
        assertNamespaceUnchanged(request.getNamespace(), existing.getNamespace());
        copyIdentity(request, existing);
        request.setRuleTree(RuleTreeCompiler.canonicalizeLenient(request.getRuleTree()));
        if (flowDao.updateDraft(flowId, request) == 0) {
            throw immutableVersion(existing);
        }
        referenceDao.replaceForFlow(existing.getId(), Set.of(), Set.of());
        return flowDao.findByVersion(flowId, request.getVersion());
    }

    /** 仅更新指定草稿版本的名称和描述 */
    @Transactional
    public RuleFlowEntity updateMeta(String flowId, RuleFlowEntity request) {
        requireVersion(request, "update");
        RuleFlowEntity existing = requireFlowVersion(flowId, request.getVersion());
        requireDraft(existing);
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("flow name is required");
        }
        assertNamespaceUnchanged(request.getNamespace(), existing.getNamespace());
        if (flowDao.updateMeta(flowId, request.getName(), request.getDescription(), request.getVersion()) == 0) {
            throw immutableVersion(existing);
        }
        return flowDao.findByVersion(flowId, request.getVersion());
    }

    /** 全量校验并发布指定草稿版本 */
    @Transactional
    public RuleFlowEntity publish(String flowId, RuleFlowEntity request) {
        requireVersion(request, "publish");
        RuleFlowEntity existing = requireFlowVersion(flowId, request.getVersion());
        requireDraft(existing);
        basicCheck(request);
        assertNamespaceUnchanged(request.getNamespace(), existing.getNamespace());
        CompileResult compiled = RuleTreeCompiler.compile(request.getRuleTree());
        VerifiedReferences references = verifyReferences(compiled, existing.getNamespaceId());
        copyIdentity(request, existing);
        request.setStatus(PUBLISHED);
        request.setRuleTree(compiled.getCanonicalJson());
        flowDao.historizeActive(existing.getFlowKey());
        if (flowDao.publishDraft(flowId, request) == 0) {
            throw immutableVersion(existing);
        }
        referenceDao.replaceForFlow(existing.getId(),
                references.ruleDatabaseIds(), references.flowDatabaseIds());
        referenceDao.retargetActiveReferences(existing.getFlowKey(), existing.getId());
        suiteManager.refreshAfterCommit();
        return flowDao.findByVersion(flowId, request.getVersion());
    }

    /** 停用指定的当前发布版本 */
    @Transactional
    public void disable(String flowId, long version) {
        RuleFlowEntity existing = requireFlowVersion(flowId, version);
        if (existing.getStatus() == null || existing.getStatus() != PUBLISHED) {
            throw new BusinessException(409, "only the current published version can be disabled");
        }
        if (flowDao.disable(flowId, version) == 0) {
            throw new BusinessException(409, "flow version is no longer current");
        }
        suiteManager.refreshAfterCommit();
    }

    /** 按 flowId 查询默认编辑版本或指定业务版本 */
    public RuleFlowEntity findByFlowId(String flowId, Long version) {
        return version == null ? flowDao.findByFlowId(flowId) : flowDao.findByVersion(flowId, version);
    }

    /** 查询业务场景的全部版本 */
    public List<RuleFlowEntity> versions(String flowId) {
        RuleFlowEntity flow = flowDao.findByFlowId(flowId);
        if (flow == null) {
            throw new BusinessException(404, "flow not found: " + flowId);
        }
        return flowDao.listVersions(flowId);
    }

    /** 分页查询规则流 */
    public Map<String, Object> page(Integer status, String namespace, String keyword,
                                    int pageNumber, int pageSize) {
        int page = Math.max(pageNumber, 1);
        int size = Math.max(1, Math.min(pageSize, 200));
        long offset = (long) (page - 1) * size;
        List<RuleFlowEntity> rows = flowDao.list(status, namespace, keyword, offset, size);
        rows.forEach(this::attachListSummary);
        int total = flowDao.count(status, namespace, keyword);
        return Map.of("items", rows, "total", total, "pageNumber", page, "pageSize", size);
    }

    /** 查询编辑器使用的轻量启用规则流索引 */
    public List<Map<String, Object>> activeReferences(String namespace) {
        return flowDao.listActive(AtomicRuleService.normalizeNamespace(namespace)).stream()
                .map(flow -> Map.<String, Object>of(
                        "flowId", flow.getFlowId(),
                        "name", flow.getName(),
                        "description", flow.getDescription(),
                        "namespace", flow.getNamespace()))
                .toList();
    }

    /** 保存前试算并返回规范 JSON、DSL 和两类引用 */
    public Map<String, Object> dryRun(String ruleTree, String namespace) {
        RuleNamespaceEntity scope = requireNamespace(namespace);
        CompileResult compiled = RuleTreeCompiler.compile(ruleTree);
        VerifiedReferences references = verifyReferences(compiled, scope.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("canonicalJson", compiled.getCanonicalJson());
        result.put("dsl", compiled.getDsl());
        result.put("referencedAtomicRuleIds", references.externalRuleIds());
        result.put("referencedFlowIds", references.externalFlowIds());
        return result;
    }

    /** 为列表实体附加节点数和引用摘要 */
    private void attachListSummary(RuleFlowEntity flow) {
        TreeNode tree = TreeNode.fromJson(flow.getRuleTree());
        int[] nodes = {0};
        tree.visit((node, ignored) -> nodes[0]++, null);
        List<String> referenced = tree.collect().stream()
                .filter(RuleFlowService::isBusinessId)
                .toList();
        flow.setNodeCount(nodes[0]);
        flow.setReferencedRuleIds(referenced);
        flow.setRuleTree(null);
    }

    /** 校验所有引用的类型、状态、规则分类和命名空间 */
    private VerifiedReferences verifyReferences(CompileResult compiled, long namespaceId) {
        Set<String> invalidConditions = compiled.getConditionReferenced().stream()
                .filter(ref -> !CONDITION_BUILTINS.contains(ref))
                .filter(ref -> !RuleIds.isRuleId(ref))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> invalidActions = compiled.getActionReferenced().stream()
                .filter(ref -> !ACTION_BUILTINS.contains(ref))
                .filter(ref -> !RuleIds.isRuleId(ref) && !RuleIds.isFlowId(ref))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!invalidConditions.isEmpty() || !invalidActions.isEmpty()) {
            throw new BusinessException(400,
                    "unsupported references; condition refs=" + invalidConditions + ", action refs=" + invalidActions);
        }

        Set<String> ruleIds = compiled.getReferenced().stream()
                .filter(RuleIds::isRuleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> flowIds = compiled.getReferenced().stream()
                .filter(RuleIds::isFlowId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, AtomicRuleEntity> atomics = atomicRuleDao.findByRuleIds(ruleIds).stream()
                .collect(Collectors.toMap(AtomicRuleEntity::getRuleId, Function.identity()));
        Map<String, RuleFlowEntity> flows = flowDao.findByFlowIds(flowIds).stream()
                .collect(Collectors.toMap(RuleFlowEntity::getFlowId, Function.identity()));

        Set<String> unavailableAtomics = ruleIds.stream()
                .filter(ref -> !isActiveInNamespace(atomics.get(ref), namespaceId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> unavailableFlows = flowIds.stream()
                .filter(ref -> !isPublishedInNamespace(flows.get(ref), namespaceId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unavailableAtomics.isEmpty() || !unavailableFlows.isEmpty()) {
            throw new BusinessException(400,
                    "references must exist, be active and use the same namespace; atomic refs="
                            + unavailableAtomics + ", flow refs=" + unavailableFlows);
        }

        Set<String> wrongConditionKind = compiled.getConditionReferenced().stream()
                .filter(RuleIds::isRuleId)
                .filter(ref -> !"condition".equals(atomics.get(ref).getKind()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> wrongActionKind = compiled.getActionReferenced().stream()
                .filter(RuleIds::isRuleId)
                .filter(ref -> !"action".equals(atomics.get(ref).getKind()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!wrongConditionKind.isEmpty() || !wrongActionKind.isEmpty()) {
            throw new BusinessException(400,
                    "rule kind does not match node position; condition refs=" + wrongConditionKind
                            + ", action refs=" + wrongActionKind);
        }
        Set<Long> ruleDatabaseIds = atomics.values().stream()
                .map(AtomicRuleEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> flowDatabaseIds = flows.values().stream()
                .map(RuleFlowEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new VerifiedReferences(ruleIds, flowIds, ruleDatabaseIds, flowDatabaseIds);
    }

    /** 判断原子规则是否启用且属于指定命名空间 */
    private static boolean isActiveInNamespace(AtomicRuleEntity rule, long namespaceId) {
        return rule != null && rule.getStatus() != null && rule.getStatus() == 1
                && rule.getNamespaceId() != null && rule.getNamespaceId() == namespaceId;
    }

    /** 判断规则流是否已发布且属于指定命名空间 */
    private static boolean isPublishedInNamespace(RuleFlowEntity flow, long namespaceId) {
        return flow != null && flow.getStatus() != null && flow.getStatus() == PUBLISHED
                && flow.getNamespaceId() != null && flow.getNamespaceId() == namespaceId;
    }

    /** 判断是否为 Web 业务引用 ID */
    private static boolean isBusinessId(String value) {
        return RuleIds.isRuleId(value) || RuleIds.isFlowId(value);
    }

    /** 查询必须存在的规则流 */
    private RuleFlowEntity requireFlow(String flowId) {
        RuleFlowEntity flow = flowDao.findByFlowId(flowId);
        if (flow == null) {
            throw new BusinessException(404, "flow not found: " + flowId);
        }
        return flow;
    }

    /** 查询必须存在的指定业务版本 */
    private RuleFlowEntity requireFlowVersion(String flowId, long version) {
        RuleFlowEntity flow = flowDao.findByVersion(flowId, version);
        if (flow == null) {
            throw new BusinessException(404, "flow version not found: " + flowId + " V" + version);
        }
        return flow;
    }

    /** 查询必须存在且启用的命名空间 */
    private RuleNamespaceEntity requireNamespace(String code) {
        String normalized = AtomicRuleService.normalizeNamespace(code);
        RuleNamespaceEntity namespace = namespaceDao.findByCode(normalized);
        if (namespace == null || namespace.getStatus() == null || namespace.getStatus() != 1) {
            throw new BusinessException(400, "unknown or disabled namespace: " + normalized);
        }
        return namespace;
    }

    /** 校验规则流基础字段 */
    private static void basicCheck(RuleFlowEntity request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("flow name is required");
        }
        if (request.getRuleTree() == null || request.getRuleTree().isBlank()) {
            throw new IllegalArgumentException("ruleTree is required");
        }
    }

    /** 拷贝不可变身份和命名空间字段 */
    private static void copyIdentity(RuleFlowEntity request, RuleFlowEntity existing) {
        request.setId(existing.getId());
        request.setFlowKey(existing.getFlowKey());
        request.setNamespaceId(existing.getNamespaceId());
        request.setNamespace(existing.getNamespace());
    }

    /** 校验请求版本 */
    private static void requireVersion(RuleFlowEntity request, String operation) {
        if (request.getVersion() == null) {
            throw new IllegalArgumentException("version is required for " + operation);
        }
    }

    /** 已发布、停用和历史版本都不可修改 */
    private static void requireDraft(RuleFlowEntity flow) {
        if (flow.getStatus() == null || flow.getStatus() != DRAFT) {
            throw immutableVersion(flow);
        }
    }

    /** 创建不可修改版本异常 */
    private static BusinessException immutableVersion(RuleFlowEntity flow) {
        return new BusinessException(409,
                "published flow versions are immutable; create a new draft from "
                        + flow.getFlowId() + " V" + flow.getVersion());
    }

    /** 规则流创建后不允许改变命名空间 */
    private static void assertNamespaceUnchanged(String requested, String existing) {
        if (requested != null && !requested.isBlank() && !existing.equals(requested)) {
            throw new BusinessException(400, "namespace cannot be changed after creation");
        }
    }

    /** 发布校验后的两类引用集合 */
    private record VerifiedReferences(Set<String> externalRuleIds,
                                      Set<String> externalFlowIds,
                                      Set<Long> ruleDatabaseIds,
                                      Set<Long> flowDatabaseIds) {
    }
}
