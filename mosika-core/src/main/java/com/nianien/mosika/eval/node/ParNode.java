package com.nianien.mosika.eval.node;


import com.nianien.mosika.eval.EvalNode;
import com.nianien.mosika.eval.context.RuleContext;
import com.nianien.mosika.eval.result.EvalResult;
import com.nianien.mosika.exception.RuleEvalException;
import com.nianien.mosika.utils.Constants;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
public class ParNode extends AbstractRuleNode {

    /**
     * 待并发执行的分支节点。
     */
    private final List<RuleNode> nodes = new ArrayList<>();

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
        nodes.add(node);
        resetExpr();
        return this;
    }

    /**
     * 在公共线程池中执行所有非 {@link Constants#NOP} 分支，并等待全部分支完成。
     *
     * @param context 规则执行上下文
     * @return 无业务结果的结构执行结果
     */
    @Override
    public EvalResult eval(RuleContext context) {
        EvalNode parentNode = context.getCurrentEval();
        List<CompletableFuture<?>> pending = new ArrayList<>(nodes.size());
        for (RuleNode node : nodes) {
            if (!Constants.NOP.equals(node.expr())) {
                pending.add(CompletableFuture.runAsync(() -> {
                    // 子线程设置当前评估节点
                    context.setCurrentEval(parentNode);
                    try {
                        context.visit(node);
                    } finally {
                        context.setCurrentEval(null);
                    }
                }, ForkJoinPool.commonPool()));
            }
        }
        CompletableFuture<?>[] futures = pending.toArray(new CompletableFuture[0]);

        try {
            CompletableFuture.allOf(futures).get(1, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuleEvalException(expr(), cause == null ? e.getMessage() : cause.getMessage(), cause);
        } catch (InterruptedException e) {
            cancelPending(futures);
            Thread.currentThread().interrupt();
            throw new RuleEvalException(expr(), "parallel rule execution interrupted", e);
        } catch (TimeoutException e) {
            cancelPending(futures);
            throw new RuleEvalException(expr(), "parallel rule execution timed out after 1 minute", e);
        } finally {
            // 线程策略可能使用当前线程，需要恢复父评估节点
            context.setCurrentEval(parentNode);
        }
        return new EvalResult(expr(), null, true);
    }

    /**
     * 取消尚未开始执行的分支，不尝试中断已在运行的分支。
     */
    private static void cancelPending(CompletableFuture<?>[] futures) {
        for (CompletableFuture<?> future : futures) {
            future.cancel(false);
        }
    }


    /**
     * 使用 {@code =>} 连接所有分支表达式。
     *
     * @return 并行 DSL 表达式
     */
    @Override
    protected String computeExpr() {
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
