package com.skyfalling.mosika.eval.node;

import com.skyfalling.mosika.eval.context.RuleContext;
import com.skyfalling.mosika.eval.result.EvalResult;

/**
 * 规则语法树的统一节点接口
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public interface RuleNode {


    /**
     * 在指定上下文中执行或评估当前节点
     *
     * @param context 规则执行上下文
     * @return 当前节点的评估结果
     */
    EvalResult eval(RuleContext context);


    /**
     * 返回当前节点对应的 DSL 表达式
     *
     * @return DSL 表达式
     */
    String expr();

    /**
     * 将当前节点与指定节点组合为逻辑与
     *
     * @param node 右侧节点
     * @return 逻辑与节点
     */
    default RuleNode and(RuleNode node) {
        return new AndNode(this, node);
    }

    /**
     * 将当前节点与指定节点组合为逻辑或
     *
     * @param node 右侧节点
     * @return 逻辑或节点
     */
    default RuleNode or(RuleNode node) {
        return new OrNode(this, node);
    }

    /**
     * 对当前节点的匹配结果取反
     *
     * @return 逻辑非节点
     */
    default RuleNode not() {
        return new NotNode(this);
    }


    /**
     * 添加后继节点
     * <p>
     * 默认创建串行执行结构，组合节点可以重写为自身的追加语义
     *
     * @param node 后继节点
     * @return 组合后的规则节点
     */
    default RuleNode next(RuleNode node) {
        return new SerNode(this, node);
    }
}
