package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;
import com.nianien.mosika.udf.Functions;

@Udf(group = "javaFns", value = "add")
public class JavaAddUdf implements Functions.Function2<Integer, Integer, Integer> {

    @Override
    @UdfMethod(description = "返回两个整数之和")
    public Integer apply(Integer left, Integer right) {
        return left + right;
    }
}
