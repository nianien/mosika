package com.skyfalling.mosika.ui.web.service;

import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.ui.tree.node.TreeNode;
import com.skyfalling.mosika.ui.tree.visitor.TreeVisitor;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * RuleFlow 保存前的 UI 树校验与单向编译器
 * <p>
 * 把请求 JSON 反序列化为 {@link TreeNode}，完成规模与结构校验后单向编译为
 * {@link RuleNode}，同时生成稳定 JSON、可执行 DSL 和分类后的叶子引用集合
 * <p>
 * 编译结果不提供从执行节点反向恢复 UI 树的能力，UI AST 始终是编辑与持久化的事实来源
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public final class RuleTreeCompiler {

    /** 单棵 UI 树允许的最大递归深度 */
    public static final int MAX_TREE_DEPTH = 128;

    /** 单棵 UI 树允许的最大节点数量 */
    public static final int MAX_TOTAL_NODES = 2000;

    /** 工具类不允许实例化 */
    private RuleTreeCompiler() {
    }

    /**
     * UI 树通过严格编译后得到的持久化和运行态中间结果
     */
    @Data
    public static class CompileResult {
        /** 规范化后的 UI 树 JSON，用于入库 */
        private final String canonicalJson;
        /** 编译后的可执行 DSL（用于装配复合 RuleDefinition） */
        private final String dsl;
        /** 树中出现的所有业务引用与内置规则 */
        private final Set<String> referenced;
        /** 条件节点和规则子树中的规则引用 */
        private final Set<String> conditionReferenced;
        /** 动作节点中的原子规则、规则流或内置动作引用 */
        private final Set<String> actionReferenced;
    }

    /**
     * 严格编译客户端提交的 UI 树
     * <p>
     * 依次完成反序列化、规模校验、结构校验、执行树编译和引用收集，任一步失败都不会
     * 返回部分结果
     *
     * @param treeJson 客户端提交的 {@link TreeNode} JSON
     * @return 可直接持久化并用于装配复合规则的编译结果
     * @throws IllegalArgumentException JSON、树规模、节点结构或执行树编译无效时抛出
     */
    public static CompileResult compile(String treeJson) {
        if (treeJson == null || treeJson.isBlank()) {
            throw new IllegalArgumentException("ruleTree cannot be blank");
        }
        TreeNode tree;
        try {
            tree = TreeNode.fromJson(treeJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("ruleTree is not a valid TreeNode JSON: " + e.getMessage(), e);
        }
        if (tree == null) {
            throw new IllegalArgumentException("ruleTree deserialized to null");
        }
        try {
            tree.validateSize(MAX_TREE_DEPTH, MAX_TOTAL_NODES);
            tree.validate();
        } catch (Exception e) {
            throw new IllegalArgumentException("ruleTree structural validation failed: " + e.getMessage(), e);
        }
        RuleNode ruleNode;
        try {
            ruleNode = tree.toRule();
        } catch (Exception e) {
            throw new IllegalArgumentException("ruleTree compile failed: " + e.getMessage(), e);
        }
        Set<String> conditionReferenced = tree.visit(
                TreeVisitor.CONDITION_RULE_ID_COLLECTOR, new LinkedHashSet<>());
        Set<String> actionReferenced = tree.visit(
                TreeVisitor.ACTION_RULE_ID_COLLECTOR, new LinkedHashSet<>());
        Set<String> collected = new LinkedHashSet<>(conditionReferenced);
        collected.addAll(actionReferenced);
        return new CompileResult(tree.toJson(), ruleNode.expr(), collected,
                conditionReferenced, actionReferenced);
    }

    /**
     * 对草稿 UI 树执行宽松规范化
     * <p>
     * 只要求 JSON 能反序列化为 {@link TreeNode} 且规模不超过限制，不执行完整结构校验
     * 和执行树编译，因此允许保存尚未完成的编辑状态
     *
     * @param treeJson 客户端提交的草稿 UI 树 JSON
     * @return 由 {@link TreeNode#toJson()} 生成的规范 JSON
     * @throws IllegalArgumentException JSON 无效、结果为空或树规模超过限制时抛出
     */
    public static String canonicalizeLenient(String treeJson) {
        if (treeJson == null || treeJson.isBlank()) {
            throw new IllegalArgumentException("ruleTree cannot be blank");
        }
        TreeNode tree;
        try {
            tree = TreeNode.fromJson(treeJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("ruleTree is not a valid TreeNode JSON: " + e.getMessage(), e);
        }
        if (tree == null) {
            throw new IllegalArgumentException("ruleTree deserialized to null");
        }
        try {
            tree.validateSize(MAX_TREE_DEPTH, MAX_TOTAL_NODES);
        } catch (Exception e) {
            throw new IllegalArgumentException("ruleTree size validation failed: " + e.getMessage(), e);
        }
        return tree.toJson();
    }
}
