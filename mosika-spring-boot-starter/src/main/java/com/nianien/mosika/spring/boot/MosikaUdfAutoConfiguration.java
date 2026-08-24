package com.nianien.mosika.spring.boot;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Mosika Java UDF 扫描与加载的 Spring Boot 自动配置
 */
@AutoConfiguration
@Import(UdfBeanDefinitionRegistrar.class)
public class MosikaUdfAutoConfiguration {

    /**
     * 创建 Spring UDF 定义加载器
     *
     * @param beanFactory 当前应用的 Bean 工厂
     * @return Spring UDF 定义加载器
     */
    @Bean
    @ConditionalOnMissingBean
    SpringUdfLoader springUdfLoader(ConfigurableListableBeanFactory beanFactory) {
        return new SpringUdfLoader(beanFactory);
    }
}
