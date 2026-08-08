package com.skyfalling.mosika.eval.node;

import com.skyfalling.mosika.eval.context.RuleContext;
import com.skyfalling.mosika.eval.result.EvalResult;
import com.skyfalling.mosika.utils.Constants;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 串行执行结构节点。
 * <p>按声明顺序执行所有非 {@link Constants#NOP} 占位子节点，不解释子节点的匹配结果。
 * 正常完成时不产生业务结果，统一返回 {@code result=null, matched=true}；
 * 子节点抛出的异常继续向上传播。</p>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class SerNode implements RuleNode {

    /**
     * 按执行顺序保存的子节点。
     */
    private final List<RuleNode> nodes = new ArrayList<>();


    /**
     * 创建串行执行结构。
     *
     * @param nodes 按执行顺序排列的子节点
     */
    public SerNode(RuleNode... nodes) {
        this.nodes.addAll(Arrays.asList(nodes));
    }


    /**
     * 向当前串行结构末尾追加一个步骤。
     *
     * @param node 待追加的后继节点
     * @return 当前串行节点
     */
    public SerNode next(RuleNode node) {
        nodes.add(node);
        return this;
    }

    /**
     * 依次访问所有非 {@link Constants#NOP} 子节点。
     *
     * @param context 规则执行上下文
     * @return 无业务结果的结构执行结果
     */
    @Override
    public EvalResult eval(RuleContext context) {
        nodes.stream()
                .filter(node -> !Constants.NOP.equals(node.expr()))
                .forEach(context::visit);
        return new EvalResult(expr(), null, true);
    }


    /**
     * 使用 {@code ->} 连接所有子节点表达式。
     *
     * @return 串行 DSL 表达式
     */
    @Override
    public String expr() {
        return String.join("->", nodes.stream()
                .map(Objects::toString/*RuleNode::expr*/)
                .collect(Collectors.toList()));
    }

    /**
     * 返回带括号的串行 DSL 表达式。
     *
     * @return 带括号的串行 DSL 表达式
     */
    @Override
    public String toString() {
        return "(" + expr() + ")";
    }
}
