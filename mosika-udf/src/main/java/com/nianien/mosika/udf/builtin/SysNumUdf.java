package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 精确十进制运算内置 UDF，调用形如 {@code sys.num.divide(a, b, 2)}。
 * <p>
 * JavaScript 只有 double，金额与比例计算会丢失精度；本组基于 {@link BigDecimal}
 * 提供精确运算。数值以字符串实参传入可保证精确度，例如 {@code sys.num.add("0.1", "0.2")}。
 */
@Udf(group = "sys", value = "num")
public class SysNumUdf {

    @UdfMethod(description = "精确加法 a + b")
    public BigDecimal add(BigDecimal a, BigDecimal b) {
        return a.add(b);
    }

    @UdfMethod(description = "精确减法 a - b")
    public BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return a.subtract(b);
    }

    @UdfMethod(description = "精确乘法 a * b")
    public BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return a.multiply(b);
    }

    @UdfMethod(description = "精确除法 a / b，保留 scale 位小数，四舍五入")
    public BigDecimal divide(BigDecimal a, BigDecimal b, int scale) {
        return a.divide(b, scale, RoundingMode.HALF_UP);
    }

    @UdfMethod(description = "四舍五入到 scale 位小数")
    public BigDecimal round(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    @UdfMethod(description = "比较 a 与 b，返回 -1 / 0 / 1")
    public int compare(BigDecimal a, BigDecimal b) {
        return a.compareTo(b);
    }

    @UdfMethod(description = "百分比 part / whole * 100，保留 scale 位小数")
    public BigDecimal percent(BigDecimal part, BigDecimal whole, int scale) {
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, scale, RoundingMode.HALF_UP);
    }

    @UdfMethod(description = "绝对值")
    public BigDecimal abs(BigDecimal value) {
        return value.abs();
    }

    @UdfMethod(description = "取较大值")
    public BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.max(b);
    }

    @UdfMethod(description = "取较小值")
    public BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.min(b);
    }
}
