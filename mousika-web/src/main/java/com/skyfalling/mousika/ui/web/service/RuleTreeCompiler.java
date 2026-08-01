package com.skyfalling.mousika.ui.web.service;

import com.skyfalling.mousika.eval.node.RuleNode;
import com.skyfalling.mousika.ui.tree.node.TreeNode;
import com.skyfalling.mousika.ui.tree.visitor.TreeVisitor;
import com.skyfalling.mousika.utils.JsonUtils;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 规则流保存前的校验与规范化：把请求的 UI 树 JSON 反序列化为 {@link TreeNode}，
 * 结构校验、编译到内核 {@link RuleNode}、抽取叶子引用集合，并回吐一个稳定的
 * 规范 JSON（{@code TreeNode.toJson()} 的产物）以及可执行 DSL。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public final class RuleTreeCompiler {

    public static final int MAX_TREE_DEPTH = 128;
    public static final int MAX_TOTAL_NODES = 2000;

    private RuleTreeCompiler() {
    }

    @Data
    public static class CompileResult {
        /** 规范化后的 UI 树 JSON（用于入库）。 */
        private final String canonicalJson;
        /** 编译后的可执行 DSL（用于装配 RuleFlowDefinition）。 */
        private final String dsl;
        /** 树中出现的所有原子规则 id 字符串（含数字与非数字，例如 true/false）。 */
        private final Set<String> referenced;
        /** 条件节点和规则子树引用。 */
        private final Set<String> conditionReferenced;
        /** 动作节点引用。 */
        private final Set<String> actionReferenced;
    }

    /**
     * 反序列化 → 结构校验 → 编译 → 收集引用；任一步失败以 {@link IllegalArgumentException} 抛出。
     *
     * @param treeJson 客户端提交的 UI 树 JSON（TreeNode 的形态）
     */
    public static CompileResult compile(String treeJson) {
        if (treeJson == null || treeJson.isBlank()) {
            throw new IllegalArgumentException("ruleTree cannot be blank");
        }
        TreeNode tree;
        try {
            tree = JsonUtils.toBean(treeJson, TreeNode.class);
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
     * 草稿保存用的宽松规范化：仅要求 JSON 可反序列化为 {@link TreeNode}（防止存入垃圾），
     * 返回规范 JSON，但不做结构校验、不编译。无法解析则抛 {@link IllegalArgumentException}。
     */
    public static String canonicalizeLenient(String treeJson) {
        if (treeJson == null || treeJson.isBlank()) {
            throw new IllegalArgumentException("ruleTree cannot be blank");
        }
        TreeNode tree;
        try {
            tree = JsonUtils.toBean(treeJson, TreeNode.class);
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
