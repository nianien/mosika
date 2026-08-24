package com.nianien.mosika.spring.boot;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.udf.UdfDelegate;
import com.nianien.mosika.udf.UdfDefinition;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpringUdfLoader {

    private final ConfigurableListableBeanFactory beanFactory;

    public SpringUdfLoader(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * 加载 Spring 容器中全部 {@link Udf} Bean 并创建 Core UDF 定义
     * <p>
     * 注解与泛型签名从 AOP 目标类读取，反射调用方法映射到实际代理 Bean
     *
     * @return 按 Spring Bean 名稳定加载的 UDF 定义
     */
    public List<UdfDefinition> loadUdfs() {
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        Arrays.sort(beanNames);
        List<UdfDefinition> definitions = new ArrayList<>();
        for (String beanName : beanNames) {
            Class<?> beanType = beanFactory.getBeanDefinition(beanName).getResolvableType().resolve();
            Udf annotation = beanType == null ? null
                    : AnnotatedElementUtils.findMergedAnnotation(beanType, Udf.class);
            if (annotation == null) {
                annotation = beanFactory.findAnnotationOnBean(beanName, Udf.class);
            }
            if (annotation == null) {
                continue;
            }
            Object bean = beanFactory.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, Udf.class);
            String name = annotation.value().isEmpty()
                    ? StringUtils.uncapitalizeAsProperty(targetClass.getSimpleName())
                    : annotation.value();
            UdfDelegate<?, ?> delegate = UdfDelegate.of(bean, targetClass,
                    method -> resolveInvocableMethod(bean, targetClass, method));
            definitions.add(new UdfDefinition(annotation.group(), name, delegate));
        }
        return definitions;
    }

    /**
     * 将目标类方法映射为能够在实际 Spring Bean 上调用的方法
     * <p>
     * JDK 代理可能只暴露擦除后的接口方法，因此需要还原最具体方法并解析 bridge method
     *
     * @param bean         实际 Spring Bean
     * @param targetClass  AOP 目标类
     * @param targetMethod 目标类上已暴露的 UDF 方法
     * @return 可在实际 Bean 上调用的方法
     */
    private static Method resolveInvocableMethod(Object bean, Class<?> targetClass, Method targetMethod) {
        for (Method candidate : bean.getClass().getMethods()) {
            if (!candidate.getName().equals(targetMethod.getName())
                    || candidate.getParameterCount() != targetMethod.getParameterCount()) {
                continue;
            }
            Method specificMethod = AopUtils.getMostSpecificMethod(candidate, targetClass);
            if (BridgeMethodResolver.findBridgedMethod(specificMethod).equals(targetMethod)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("udf method is not invocable through Spring proxy: "
                + targetClass.getName() + "." + targetMethod.getName());
    }
}
