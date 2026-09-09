package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 集合运算内置 UDF，调用形如 {@code sys.coll.sum(list)}。
 * <p>
 * 面向规则里从业务对象取出的列表，提供计数、去重、求和与极值等常用操作。
 */
@Udf(group = "sys", value = "coll")
public class SysCollUdf {

    @UdfMethod(description = "元素个数")
    public int size(List<Object> list) {
        return list.size();
    }

    @UdfMethod(description = "是否为空")
    public boolean isEmpty(List<Object> list) {
        return list.isEmpty();
    }

    @UdfMethod(description = "是否包含指定元素")
    public boolean contains(List<Object> list, Object value) {
        return list.contains(value);
    }

    @UdfMethod(description = "去重，保留首次出现顺序")
    public List<Object> distinct(List<Object> list) {
        return new ArrayList<>(new LinkedHashSet<>(list));
    }

    @UdfMethod(description = "用 separator 连接为字符串")
    public String join(List<Object> list, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                builder.append(separator);
            }
            builder.append(list.get(i));
        }
        return builder.toString();
    }

    @UdfMethod(description = "精确求和")
    public BigDecimal sum(List<BigDecimal> list) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : list) {
            total = total.add(value);
        }
        return total;
    }

    @UdfMethod(description = "最大值")
    public BigDecimal max(List<BigDecimal> list) {
        BigDecimal result = list.get(0);
        for (BigDecimal value : list) {
            result = result.max(value);
        }
        return result;
    }

    @UdfMethod(description = "最小值")
    public BigDecimal min(List<BigDecimal> list) {
        BigDecimal result = list.get(0);
        for (BigDecimal value : list) {
            result = result.min(value);
        }
        return result;
    }
}
