package com.skyfalling.mosika.eval;

import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.eval.result.EvalResult;
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
     * 父执行节点
     */
    private EvalNode parent;

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
        this.ruleNode = ruleNode;
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
