package com.skyfalling.mousika.eval.node;


import com.skyfalling.mousika.eval.EvalNode;
import com.skyfalling.mousika.eval.context.RuleContext;
import com.skyfalling.mousika.eval.result.EvalResult;
import com.skyfalling.mousika.utils.Constants;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 并行执行结构节点。
 * <p>并发执行所有非 {@link Constants#NOP} 占位子节点并等待全部完成，不解释子节点的匹配结果。
 * 正常完成时不产生业务结果，统一返回 {@code result=null, matched=true}；
 * 子节点抛出的异常继续向上传播。</p>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class ParNode implements RuleNode {

    /**
     * 待并发执行的分支节点。
     */
    private List<RuleNode> nodes = new ArrayList<>();

    /**
     * 创建并行执行结构。
     *
     * @param nodes 并行分支节点
     */
    public ParNode(RuleNode... nodes) {
        this.nodes.addAll(Arrays.asList(nodes));
    }


    /**
     * 向当前并行结构追加一个分支。
     *
     * @param node 待追加的并行分支
     * @return 当前并行节点
     */
    @Override
    public ParNode next(RuleNode node) {
        this.nodes.add(node);
        return this;
    }

    /**
     * 在公共线程池中执行所有非 {@link Constants#NOP} 分支，并等待全部分支完成。
     *
     * @param context 规则执行上下文
     * @return 无业务结果的结构执行结果
     */
    @Override
    @SneakyThrows
    public EvalResult eval(RuleContext context) {
        EvalNode parentNode = context.getCurrentEval();
        CompletableFuture<?>[] futures = nodes.stream()
                .filter(node -> !Constants.NOP.equals(node.expr()))
                .map(node -> CompletableFuture.runAsync(() -> {
                    // 子线程设置当前评估节点
                    context.setCurrentEval(parentNode);
                    context.visit(node);
                }, ForkJoinPool.commonPool()))
                .toArray(n -> new CompletableFuture[n]);

        try {
            CompletableFuture.allOf(futures).get(1, TimeUnit.MINUTES);
        } finally {
            // 线程策略可能使用当前线程，需要恢复父评估节点
            context.setCurrentEval(parentNode);
        }
        return new EvalResult(expr(), null, true);
    }


    /**
     * 使用 {@code =>} 连接所有分支表达式。
     *
     * @return 并行 DSL 表达式
     */
    @Override
    public String expr() {
        return String.join("=>", nodes.stream()
                .map(Objects::toString/*RuleNode::expr*/)
                .collect(Collectors.toList()));
    }

    /**
     * 多分支时返回带括号的并行 DSL 表达式。
     *
     * @return 并行 DSL 表达式
     */
    @Override
    public String toString() {
        return nodes.size() > 1 ? "(" + expr() + ")" : expr();
    }
}
