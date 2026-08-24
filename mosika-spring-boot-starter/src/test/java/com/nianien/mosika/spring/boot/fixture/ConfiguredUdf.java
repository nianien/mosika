package com.nianien.mosika.spring.boot.fixture;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;

@Udf(group = "math", value = "configured")
public class ConfiguredUdf {

    @UdfMethod
    public Integer apply(Integer value) {
        return value;
    }
}
