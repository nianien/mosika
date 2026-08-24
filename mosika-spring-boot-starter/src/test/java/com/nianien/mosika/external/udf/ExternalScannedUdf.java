package com.nianien.mosika.external.udf;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;
import com.nianien.mosika.spring.boot.fixture.UdfDependency;

@Udf(group = "external", value = "scanned")
public class ExternalScannedUdf {

    private final UdfDependency dependency;

    public ExternalScannedUdf(UdfDependency dependency) {
        this.dependency = dependency;
    }

    @UdfMethod
    public Integer apply(Integer value) {
        return value * dependency.factor();
    }

    public UdfDependency dependency() {
        return dependency;
    }
}
