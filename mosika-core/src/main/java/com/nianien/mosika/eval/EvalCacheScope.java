package com.nianien.mosika.eval;

import com.nianien.mosika.eval.result.EvalResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 单个求值阶段共享的叶子结果缓存
 */
public class EvalCacheScope {

    private volatile Map<String, EvalResult> results = new ConcurrentHashMap<>();

    /**
     * 创建空缓存作用域
     */
    public EvalCacheScope() {
    }

    public EvalResult computeIfAbsent(String expression,
                                      Function<String, EvalResult> evaluator) {
        return results.computeIfAbsent(expression, evaluator);
    }

    /**
     * 清空当前缓存供下一轮求值复用
     */
    public void reset() {
        results.clear();
    }

    /**
     * 切换到新缓存并隔离仍可能写入旧缓存的并发任务
     */
    public void invalidate() {
        results = new ConcurrentHashMap<>();
    }

    public Map<String, EvalResult> currentResults() {
        return results;
    }
}
