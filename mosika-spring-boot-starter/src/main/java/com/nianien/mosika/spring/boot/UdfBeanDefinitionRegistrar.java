package com.nianien.mosika.spring.boot;

import com.nianien.mosika.annotation.Udf;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 在 Bean 定义阶段扫描并注册带 {@link Udf} 的 Java UDF 类型
 * <p>
 * 扫描范围合并 Spring Boot 基础包和显式配置包，已存在的同类型 Bean 不重复注册
 */
final class UdfBeanDefinitionRegistrar implements BeanDefinitionRegistryPostProcessor,
        BeanFactoryAware, BeanClassLoaderAware, EnvironmentAware, ResourceLoaderAware {

    /** 扩展扫描包配置项 */
    private static final String SCAN_PACKAGES_PROPERTY = "mosika.udf.scan-packages";

    private BeanFactory beanFactory;

    private ClassLoader beanClassLoader;

    private Environment environment;

    private ResourceLoader resourceLoader;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 在 Bean 实例化前注册扫描到的 UDF 定义
     * <p>
     * 候选类型已经通过 {@code @Bean} 或组件扫描注册时直接复用存量定义
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        Set<String> basePackages = new LinkedHashSet<>();
        if (AutoConfigurationPackages.has(beanFactory)) {
            basePackages.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        List<String> configuredPackages = Binder.get(environment)
                .bind(SCAN_PACKAGES_PROPERTY, Bindable.listOf(String.class))
                .orElse(List.of());
        for (String basePackage : configuredPackages) {
            if (StringUtils.hasText(basePackage)) {
                basePackages.add(basePackage.trim());
            }
        }
        if (basePackages.isEmpty()) {
            return;
        }

        ListableBeanFactory listableBeanFactory = (ListableBeanFactory) beanFactory;
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(
                registry, false, environment, resourceLoader) {
            @Override
            protected boolean checkCandidate(String beanName, BeanDefinition candidate) {
                Class<?> beanType = ClassUtils.resolveClassName(
                        candidate.getBeanClassName(), beanClassLoader);
                if (listableBeanFactory.getBeanNamesForType(beanType, true, false).length > 0) {
                    return false;
                }
                return super.checkCandidate(beanName, candidate);
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(Udf.class));
        scanner.scan(basePackages.toArray(String[]::new));
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // UDF Bean 定义已在注册阶段完成
    }
}
