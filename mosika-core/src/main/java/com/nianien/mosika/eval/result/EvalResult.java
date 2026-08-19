package com.nianien.mosika.eval.result;

import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 单个规则节点的评估结果
 * <p>
 * {@link #result} 保存业务返回值，{@link #matched} 保存条件判断使用的匹配状态
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
public class EvalResult {

    /** 字符串结果按布尔解释时的假值集合;预编译并大小写不敏感,避免每次 eval 编译正则与 toLowerCase 分配。 */
    private static final Pattern FALSY = Pattern.compile("no|false|null|0|fail", Pattern.CASE_INSENSITIVE);

    /**
     * 被评估节点表达式
     */
    protected String expr;
    /**
     * 节点业务返回值
     */
    protected final Object result;
    /**
     * 作为判断条件时使用的匹配状态
     */
    private final boolean matched;


    /**
     * 根据业务返回值计算匹配状态
     *
     * @param expr   被评估节点表达式
     * @param result 节点业务返回值
     */
    public EvalResult(String expr, Object result) {
        this.expr = expr;
        this.result = result;
        this.matched = parseBoolean(result);
    }

    /**
     * 使用指定业务返回值和匹配状态创建评估结果
     *
     * @param expr    被评估节点表达式
     * @param result  节点业务返回值
     * @param matched 匹配状态
     */
    public EvalResult(String expr, Object result, boolean matched) {
        this.expr = expr;
        this.result = result;
        this.matched = matched;
    }


    /**
     * 把业务返回值转换为匹配状态
     *
     * @param res 业务返回值
     * @return 匹配状态
     */
    private boolean parseBoolean(Object res) {
        if (res == null) {
            return false;
        }
        if (res instanceof Boolean) {
            return (Boolean) res;
        }
        if (res instanceof Number) {
            if (res instanceof BigDecimal) {
                return ((BigDecimal) res).signum() > 0;
            }
            if (res instanceof BigInteger) {
                return ((BigInteger) res).signum() > 0;
            }
            if (res instanceof Byte || res instanceof Short || res instanceof Integer || res instanceof Long) {
                return ((Number) res).longValue() > 0;
            }
            return ((Number) res).doubleValue() > 0;
        }
        if (res instanceof String) {
            return !FALSY.matcher((String) res).matches();
        }
        if (res instanceof Collection) {
            return !((Collection<?>) res).isEmpty();
        }
        if (res instanceof Map) {
            return !((Map<?, ?>) res).isEmpty();
        }
        return true;
    }
}
