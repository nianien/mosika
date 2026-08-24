package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;
import com.nianien.mosika.udf.Functions;

@Udf(group = "javaFns", value = "doubleValue")
public class JavaDoubleUdf implements Functions.Function1<Integer, Integer> {

    @Override
    @UdfMethod(description = "返回输入整数的两倍")
    public Integer apply(Integer value) {
        return value * 2;
    }
}
