package com.skyfalling.mousika.eval.node;


import com.skyfalling.mousika.eval.EvalNode;
import com.skyfalling.mousika.eval.context.RuleContext;
import com.skyfalling.mousika.eval.result.EvalResult;
import com.skyfalling.mousika.utils.Constants;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 并行执行节点<br>
 * 最后执行的节点结果作为并行结果; 如果存在节点结果为真，则并行结果作为判断条件为真,否则为假
 * Created on 2023/3/31
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class ParNode implements RuleNode {

    private List<RuleNode> nodes = new ArrayList<>();

    /**
     * @param nodes 子节点
     */
    public ParNode(RuleNode... nodes) {
        this.nodes.addAll(Arrays.asList(nodes));
    }


    /**
     * 添加下一个并行节点
     */
    @Override
    public ParNode next(RuleNode node) {
        this.nodes.add(node);
        return this;
    }

    @Override
    @SneakyThrows
    public EvalResult eval(RuleContext context) {
        EvalNode parentNode = context.getCurrentEval();
        Vector<EvalResult> results = new Vector<>();
        CompletableFuture<EvalResult>[] futures = nodes.stream()
                .filter(e -> !e.expr().equals(Constants.NOP))
                .map(node -> CompletableFuture.supplyAsync(() -> {
                            //子线程设置当前evalNode
                            context.setCurrentEval(parentNode);
                            return context.visit(node);
                        }, ForkJoinPool.commonPool()).thenAcceptAsync(r -> results.add(r))
                )
                .toArray(n -> new CompletableFuture[n]);

        CompletableFuture.allOf(futures).get(1, TimeUnit.MINUTES);
        //线程策略会使用当前线程,所以需要恢复父评估节点
        context.setCurrentEval(parentNode);
        EvalResult result = results.stream().filter(EvalResult::isMatched).findAny().orElse(new EvalResult(null, null));
        return new EvalResult(expr(), result.getResult(), result.isMatched());
    }


    @Override
    public String expr() {
        return String.join("=>", nodes.stream()
                .map(Objects::toString/*RuleNode::expr*/)
                .collect(Collectors.toList()));
    }

    @Override
    public String toString() {
        return nodes.size() > 1 ? "(" + expr() + ")" : expr();
    }
}
