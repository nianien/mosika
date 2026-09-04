package com.nianien.mosika.eval;

import com.nianien.mosika.eval.node.RuleNode;
import com.nianien.mosika.eval.result.EvalResult;
import lombok.Data;

import java.util.List;
import java.util.Vector;

/**
 * 评估节点定义
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
public class EvalNode {

    /**
     * 规则节点
     */
    private final RuleNode ruleNode;

    /**
     * 当前节点使用的叶子结果缓存作用域
     */
    private final EvalCacheScope cacheScope;

    /**
     * 当前执行路径上该节点的独立评估结果
     */
    private EvalResult result;

    /**
     * 子执行节点<br>
     * 引擎支持并行,必须线程安全
     */
    private List<EvalNode> children = new Vector<>();

    /**
     * 执行节点
     *
     * @param ruleNode
     */
    public EvalNode(RuleNode ruleNode) {
        this(ruleNode, new EvalCacheScope());
    }

    /**
     * 创建使用指定缓存作用域的执行节点
     *
     * @param ruleNode 规则节点
     * @param cacheScope 缓存作用域
     */
    public EvalNode(RuleNode ruleNode, EvalCacheScope cacheScope) {
        this.ruleNode = ruleNode;
        this.cacheScope = cacheScope;
    }

    /**
     * 添加子节点
     *
     * @param node
     */
    public void add(EvalNode node) {
        this.children.add(node);
    }

    @Override
    public String toString() {
        return ruleNode.expr();
    }
}
