package com.skyfalling.mousika.ui.web.service;

import com.skyfalling.mousika.ui.web.common.BusinessException;
import com.skyfalling.mousika.ui.web.dao.FlowRuleRefDao;
import com.skyfalling.mousika.ui.web.dao.RuleDefinitionDao;
import com.skyfalling.mousika.ui.web.dao.RuleFlowDao;
import com.skyfalling.mousika.ui.web.entity.RuleDefinitionEntity;
import com.skyfalling.mousika.ui.web.entity.RuleFlowEntity;
import com.skyfalling.mousika.ui.web.service.RuleTreeCompiler.CompileResult;
import com.skyfalling.mousika.ui.tree.node.TreeNode;
import com.skyfalling.mousika.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code rule_flow} 层的业务操作：保存前先由 {@link RuleTreeCompiler} 校验/规范化 UI 树，
 * 校验通过后落库、重建 {@code flow_rule_ref}，并触发 {@link RuleSuiteManager} 重建。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Service
@RequiredArgsConstructor
public class RuleFlowService {

    private final RuleFlowDao flowDao;
    private final RuleDefinitionDao ruleDao;
    private final FlowRuleRefDao refDao;
    private final RuleSuiteManager suiteManager;

    /** rule_flow.status 取值。 */
    public static final int DRAFT = 0;
    public static final int PUBLISHED = 1;
    public static final int DISABLED = 2;

    private static final Set<String> BUILTIN_REFERENCES = Set.of(
            Constants.TRUE, Constants.FALSE, Constants.NULL, Constants.NOP);
    private static final Set<String> CONDITION_BUILTINS = Set.of(
            Constants.TRUE, Constants.FALSE, Constants.NULL);
    private static final Set<String> ACTION_BUILTINS = Set.of(Constants.NOP);

    /** 新建：始终落为草稿（不校验语义、不进运行态）。 */
    @Transactional
    public RuleFlowEntity create(RuleFlowEntity req) {
        basicCheck(req);
        req.setRuleTree(RuleTreeCompiler.canonicalizeLenient(req.getRuleTree()));
        req.setStatus(DRAFT);
        long id = flowDao.insert(req);
        refDao.replaceForFlow(id, java.util.Collections.emptySet());
        return flowDao.findById(id);
    }

    /**
     * 保存草稿：不做结构/编译校验，仅规范化 JSON；强制 status=草稿（PUT 无法发布）。
     * 若原为已生效，转草稿后从运行态移除（refresh 只装载 published）。
     */
    @Transactional
    public RuleFlowEntity saveDraft(long id, RuleFlowEntity req) {
        RuleFlowEntity existing = requireFlow(id);
        if (req.getVersion() == null) {
            throw new IllegalArgumentException("version is required for update (expected " + existing.getVersion() + ")");
        }
        basicCheck(req);
        req.setId(id);
        req.setStatus(DRAFT);
        req.setRuleTree(RuleTreeCompiler.canonicalizeLenient(req.getRuleTree()));
        int rows = flowDao.update(req);
        if (rows == 0) {
            throw new BusinessException(409, "flow updated by others, please retry (expected version=" + req.getVersion() + ")");
        }
        // 草稿不进运行态：清空引用（活跃引用检查按 status=1 过滤，草稿本就不计入）。
        refDao.replaceForFlow(id, java.util.Collections.emptySet());
        if (existing.getStatus() != null && existing.getStatus() == PUBLISHED) {
            suiteManager.refreshAfterCommit();
        }
        return flowDao.findById(id);
    }

    /** 仅编辑元数据（名称/描述），不改树与状态、不触发运行态刷新。 */
    @Transactional
    public RuleFlowEntity updateMeta(long id, RuleFlowEntity req) {
        RuleFlowEntity existing = requireFlow(id);
        if (req.getVersion() == null) {
            throw new IllegalArgumentException("version is required for update (expected " + existing.getVersion() + ")");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("flow name is required");
        }
        int rows = flowDao.updateMeta(id, req.getName(), req.getDescription(), req.getVersion());
        if (rows == 0) {
            throw new BusinessException(409, "flow updated by others, please retry (expected version=" + req.getVersion() + ")");
        }
        return flowDao.findById(id);
    }

    /**
     * 生效：全量校验+编译，通过后以规范 JSON 落库、status=已生效、重建引用并发布进 RuleSuite。
     */
    @Transactional
    public RuleFlowEntity publish(long id, RuleFlowEntity req) {
        RuleFlowEntity existing = requireFlow(id);
        if (req.getVersion() == null) {
            throw new IllegalArgumentException("version is required for publish (expected " + existing.getVersion() + ")");
        }
        basicCheck(req);
        CompileResult compiled = RuleTreeCompiler.compile(req.getRuleTree());
        Set<Long> refIds = verifyReferences(compiled);
        req.setId(id);
        req.setStatus(PUBLISHED);
        req.setRuleTree(compiled.getCanonicalJson());
        int rows = flowDao.update(req);
        if (rows == 0) {
            throw new BusinessException(409, "flow updated by others, please retry (expected version=" + req.getVersion() + ")");
        }
        refDao.replaceForFlow(id, refIds);
        suiteManager.refreshAfterCommit();
        return flowDao.findById(id);
    }

    @Transactional
    public void disable(long id, long expectedVersion) {
        RuleFlowEntity existing = requireFlow(id);
        int rows = flowDao.disable(id, expectedVersion);
        if (rows == 0) {
            throw new BusinessException(409, "flow updated by others, please retry");
        }
        // 停用不清引用表，仅在启用集合中"生效引用"过滤时通过 f.status=1 剔除
        if (existing.getStatus() != null && existing.getStatus() == PUBLISHED) {
            suiteManager.refreshAfterCommit();
        }
    }

    public RuleFlowEntity findById(long id) {
        return flowDao.findById(id);
    }

    public java.util.Map<String, Object> page(Integer status, String keyword,
                                              int pageNumber, int pageSize) {
        int p = Math.max(pageNumber, 1);
        int s = Math.max(1, Math.min(pageSize, 200));
        long offset = (long) (p - 1) * s;
        List<RuleFlowEntity> rows = flowDao.list(status, keyword, offset, s);
        rows.forEach(this::attachListSummary);
        int total = flowDao.count(status, keyword);
        return java.util.Map.of(
                "items", rows,
                "total", total,
                "pageNumber", p,
                "pageSize", s);
    }

    /** 列表只返回绘制摘要；完整 ruleTree 由详情接口按需加载，避免每页重复传输和解析大 JSON。 */
    private void attachListSummary(RuleFlowEntity flow) {
        TreeNode tree = TreeNode.fromJson(flow.getRuleTree());
        int[] nodes = {0};
        tree.visit((node, ignored) -> nodes[0]++, null);
        List<String> referenced = tree.collect().stream()
                .filter(value -> value.chars().allMatch(Character::isDigit))
                .toList();
        flow.setNodeCount(nodes[0]);
        flow.setReferencedRuleIds(referenced);
        flow.setRuleTree(null);
    }

    /** 试算：校验并回吐规范 JSON、DSL、引用集合，不落库；供前端"保存前校验"使用。 */
    public java.util.Map<String, Object> dryRun(String ruleTree) {
        CompileResult compiled = RuleTreeCompiler.compile(ruleTree);
        Set<Long> refIds = verifyReferences(compiled);
        return java.util.Map.of(
                "canonicalJson", compiled.getCanonicalJson(),
                "dsl", compiled.getDsl(),
                "referencedRuleIds", refIds);
    }

    private RuleFlowEntity requireFlow(long id) {
        RuleFlowEntity e = flowDao.findById(id);
        if (e == null) {
            throw new BusinessException(404, "flow not found: " + id);
        }
        return e;
    }

    private void basicCheck(RuleFlowEntity req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("flow name is required");
        }
        if (req.getRuleTree() == null || req.getRuleTree().isBlank()) {
            throw new IllegalArgumentException("ruleTree is required");
        }
    }

    /**
     * 引用完整性：持久化引用必须是启用中的数字型规则 ID，只有内核常量允许使用非数字表达式；
     * 同时校验条件/动作位置与规则定义的 {@code ruleKind} 一致。
     * 返回本次 flow 需要写入 {@code flow_rule_ref} 的数字型 rule id 集合。
     */
    private Set<Long> verifyReferences(CompileResult compiled) {
        Set<Long> numeric = new HashSet<>();
        Set<String> unsupported = new HashSet<>();
        for (String s : compiled.getReferenced()) {
            try {
                numeric.add(Long.parseLong(s));
            } catch (NumberFormatException nfe) {
                if (!BUILTIN_REFERENCES.contains(s)) {
                    unsupported.add(s);
                }
            }
        }
        if (!unsupported.isEmpty()) {
            throw new BusinessException(400,
                    "ruleTree references must use numeric rule ids; unsupported refs: " + unsupported);
        }
        Set<String> invalidConditionBuiltins = nonNumericOutside(
                compiled.getConditionReferenced(), CONDITION_BUILTINS);
        Set<String> invalidActionBuiltins = nonNumericOutside(
                compiled.getActionReferenced(), ACTION_BUILTINS);
        if (!invalidConditionBuiltins.isEmpty() || !invalidActionBuiltins.isEmpty()) {
            throw new BusinessException(400,
                    "built-in reference does not match node position; condition refs="
                            + invalidConditionBuiltins + ", action refs=" + invalidActionBuiltins);
        }
        if (numeric.isEmpty()) {
            return numeric;
        }
        List<RuleDefinitionEntity> found = ruleDao.findByIds(numeric);
        Map<Long, RuleDefinitionEntity> foundById = found.stream()
                .collect(Collectors.toMap(RuleDefinitionEntity::getId, Function.identity()));
        Set<Long> foundActive = foundById.values().stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .map(RuleDefinitionEntity::getId)
                .collect(Collectors.toSet());
        Set<Long> missing = new HashSet<>(numeric);
        missing.removeAll(foundActive);
        if (!missing.isEmpty()) {
            throw new BusinessException(400,
                    "ruleTree references unknown or disabled rule ids: " + missing);
        }

        Set<Long> wrongConditionKind = numericReferences(compiled.getConditionReferenced()).stream()
                .filter(id -> !"condition".equals(foundById.get(id).getRuleKind()))
                .collect(Collectors.toSet());
        Set<Long> wrongActionKind = numericReferences(compiled.getActionReferenced()).stream()
                .filter(id -> !"action".equals(foundById.get(id).getRuleKind()))
                .collect(Collectors.toSet());
        if (!wrongConditionKind.isEmpty() || !wrongActionKind.isEmpty()) {
            throw new BusinessException(400,
                    "rule kind does not match node position; condition refs=" + wrongConditionKind
                            + ", action refs=" + wrongActionKind);
        }
        return foundActive;
    }

    private Set<Long> numericReferences(Set<String> references) {
        return references.stream()
                .filter(value -> value.chars().allMatch(Character::isDigit))
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    private Set<String> nonNumericOutside(Set<String> references, Set<String> allowed) {
        return references.stream()
                .filter(value -> !value.chars().allMatch(Character::isDigit))
                .filter(value -> !allowed.contains(value))
                .collect(Collectors.toSet());
    }
}
