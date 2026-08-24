package com.nianien.mosika.spring.boot.fixture;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;

@Udf
public class DefaultUdf {

    @UdfMethod(description = "输入值加一")
    public Integer apply(Integer value) {
        return value + 1;
    }
}
