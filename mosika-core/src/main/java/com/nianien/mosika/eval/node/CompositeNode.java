package com.nianien.mosika.eval.node;


import com.nianien.mosika.eval.context.RuleContext;
import com.nianien.mosika.eval.result.EvalResult;
import lombok.Getter;

/**
 * 保留命名规则边界的复合规则节点
 * <p>
 * 节点表达式保存复合规则 ID，内部节点保存该规则递归展开后的规则树
 * 执行时保留内部节点详情，并沿用内部节点的业务结果和匹配状态
 * <p>
 * Created on 2023/3/30
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class CompositeNode extends ExprNode {

    /**
     * 复合规则递归展开后的内部规则节点
     */
    @Getter
    private final RuleNode ruleNode;


    /**
     * 创建带调用参数的命名复合规则节点
     *
     * @param ruleId    复合规则 ID
     * @param ruleNode  递归展开后的内部规则节点
     */
    public CompositeNode(String ruleId,RuleNode ruleNode) {
        super(ruleId, null);
        this.ruleNode = ruleNode;
    }


    @Override
    public EvalResult eval(RuleContext context) {
        if (getArguments() != null) {
            throw new UnsupportedOperationException("parameterized rule execution is not supported");
        }
        EvalResult result = context.visit(ruleNode);
        EvalResult evalResult = new EvalResult(this.toString(), result.getResult(), result.isMatched());
        return evalResult;
    }


    @Override
    public String toString() {
        return this.expr() + "[" + ruleNode.expr() + "]";
    }
}
