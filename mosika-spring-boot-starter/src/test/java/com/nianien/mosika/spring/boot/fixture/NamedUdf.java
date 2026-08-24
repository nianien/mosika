package com.nianien.mosika.spring.boot.fixture;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;

@Udf(group = "math", value = "doubleValue")
public class NamedUdf {

    private final UdfDependency dependency;

    public NamedUdf(UdfDependency dependency) {
        this.dependency = dependency;
    }

    @UdfMethod(description = "按依赖提供的倍数计算")
    public Integer apply(Integer value) {
        return value * dependency.factor();
    }

    public UdfDependency dependency() {
        return dependency;
    }
}
