package com.skyfalling.mousika.eval.result;

import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

/**
 * 表达式评估结果
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
public class EvalResult {

    /**
     * 待评估表达式
     */
    protected String expr;
    /**
     * 引擎计算返回值
     */
    protected final Object result;
    /**
     * 当作为判断条件时,返回值转boolean值
     */
    private final boolean matched;


    public EvalResult(String expr, Object result) {
        this.expr = expr;
        this.result = result;
        this.matched = parseBoolean(result);
    }

    public EvalResult(String expr, Object result, boolean matched) {
        this.expr = expr;
        this.result = result;
        this.matched = matched;
    }


    /**
     * boolean值解析
     *
     * @param res
     * @return
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
            return !((String) res).toLowerCase().matches("no|false|null|0|fail");
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
