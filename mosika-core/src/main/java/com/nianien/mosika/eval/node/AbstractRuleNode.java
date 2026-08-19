package com.nianien.mosika.eval.node;

/**
 * 结构节点基类:统一 {@link #expr()} 的延迟计算与缓存。
 * <p>
 * DSL 表达式是节点结构的纯函数,且 {@code expr()} 只在求值期被调用——此时语法树已构建完成、不再变更
 * (构建/解析期不调用节点 {@code expr()})。因此首次计算即缓存,后续复用,避免每次 eval 重复用
 * stream/拼接重建字符串。子类实现 {@link #computeExpr()};构建期仍会追加子节点的可变节点,在追加后
 * 调用 {@link #resetExpr()} 作为保险(即使有人在构建期误调 {@code expr()},追加也会使缓存失效)。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public abstract class AbstractRuleNode implements RuleNode {

    private String cachedExpr;

    @Override
    public final String expr() {
        String e = cachedExpr;
        if (e == null) {
            e = computeExpr();
            cachedExpr = e;
        }
        return e;
    }

    /**
     * 计算当前节点的 DSL 表达式(由 {@link #expr()} 缓存)。
     *
     * @return DSL 表达式
     */
    protected abstract String computeExpr();

    /**
     * 使已缓存的表达式失效。仅供构建期追加子节点的可变节点在变更后调用。
     */
    protected final void resetExpr() {
        cachedExpr = null;
    }
}
