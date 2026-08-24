package com.nianien.mosika.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记允许规则脚本调用的 Java UDF 方法
 * <p>
 * 未标记方法不会进入 UDF 代理的暴露方法集合
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UdfMethod {

    /**
     * 面向管理端展示的方法说明
     *
     * @return 方法说明，未填写时为空字符串
     */
    String description() default "";
}
