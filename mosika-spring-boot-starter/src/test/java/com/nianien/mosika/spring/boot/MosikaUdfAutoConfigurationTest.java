package com.nianien.mosika.spring.boot;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;
import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.external.udf.ExternalConfiguredUdf;
import com.nianien.mosika.external.udf.ExternalScannedUdf;
import com.nianien.mosika.spring.boot.fixture.ConfiguredUdf;
import com.nianien.mosika.spring.boot.fixture.DefaultUdf;
import com.nianien.mosika.spring.boot.fixture.NamedUdf;
import com.nianien.mosika.spring.boot.fixture.UdfDependency;
import com.nianien.mosika.suite.RuleSuite;
import com.nianien.mosika.udf.Functions;
import com.nianien.mosika.udf.UdfDelegate;
import com.nianien.mosika.udf.UdfDefinition;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MosikaUdfAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MosikaUdfAutoConfiguration.class))
            .withInitializer(context -> AutoConfigurationPackages.register(
                    (BeanDefinitionRegistry) context.getBeanFactory(),
                    "com.nianien.mosika.spring.boot.fixture"))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void scansUdfBeansAndBuildsExecutableDefinitions() {
        contextRunner.run(context -> {
            SpringUdfLoader loader = context.getBean(SpringUdfLoader.class);
            List<UdfDefinition> definitions = loader.loadUdfs();

            assertEquals(3, definitions.size());
            assertEquals(List.of("defaultUdf", "math.configured", "math.doubleValue"),
                    definitions.stream().map(MosikaUdfAutoConfigurationTest::fullName).sorted().toList());
            assertEquals(DefaultUdf.class,
                    UdfDelegate.of(definition(definitions, "defaultUdf").getUdf()).type());
            assertEquals(ConfiguredUdf.class,
                    UdfDelegate.of(definition(definitions, "math.configured").getUdf()).type());
            NamedUdf namedUdf = context.getBean(NamedUdf.class);
            assertSame(context.getBean(UdfDependency.class), namedUdf.dependency());
            assertTrue(AopUtils.isAopProxy(namedUdf));
            assertEquals(NamedUdf.class,
                    UdfDelegate.of(definition(definitions, "math.doubleValue").getUdf()).type());
            assertEquals(1, context.getBeansOfType(ConfiguredUdf.class).size());

            RuleSuite suite = new RuleSuite(
                    List.of(new RuleDefinition(
                            "calculate", "defaultUdf(math.doubleValue($.value))", "calculate")),
                    definitions);
            assertEquals(7, suite.eval("calculate", java.util.Map.of("value", 3)).getResult());
        });
    }

    @Test
    void returnsEmptyDefinitionsWithoutUdfBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MosikaUdfAutoConfiguration.class))
                .withInitializer(context -> AutoConfigurationPackages.register(
                        (BeanDefinitionRegistry) context.getBeanFactory(),
                        "com.nianien.mosika.spring.boot.empty"))
                .run(context -> assertTrue(
                        context.getBean(SpringUdfLoader.class).loadUdfs().isEmpty()));
    }

    @Test
    void scansConfiguredPackagesAndReusesUdfBeansDeclaredWithBean() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MosikaUdfAutoConfiguration.class))
                .withPropertyValues(
                        "mosika.udf.scan-packages[0]=com.nianien.mosika.external",
                        "mosika.udf.scan-packages[1]=com.nianien.mosika.external.udf")
                .withUserConfiguration(ExternalTestConfiguration.class)
                .run(context -> {
                    SpringUdfLoader loader = context.getBean(SpringUdfLoader.class);
                    List<UdfDefinition> definitions = loader.loadUdfs();

                    assertEquals(List.of("external.configured", "external.scanned"),
                            definitions.stream().map(MosikaUdfAutoConfigurationTest::fullName).toList());
                    UdfDependency dependency = context.getBean(UdfDependency.class);
                    ExternalConfiguredUdf configuredUdf = context.getBean(ExternalConfiguredUdf.class);
                    ExternalScannedUdf scannedUdf = context.getBean(ExternalScannedUdf.class);
                    assertSame(dependency, configuredUdf.dependency());
                    assertSame(dependency, scannedUdf.dependency());
                    assertEquals(ExternalConfiguredUdf.class,
                            UdfDelegate.of(definition(definitions, "external.configured").getUdf()).type());
                    assertEquals(ExternalScannedUdf.class,
                            UdfDelegate.of(definition(definitions, "external.scanned").getUdf()).type());
                    assertEquals(1, context.getBeansOfType(ExternalConfiguredUdf.class).size());
                    assertEquals(1, context.getBeansOfType(ExternalScannedUdf.class).size());

                    RuleSuite suite = new RuleSuite(
                            List.of(new RuleDefinition(
                                    "external", "external.scanned(external.configured($.value))", "external")),
                            definitions);
                    assertEquals(10, suite.eval("external", java.util.Map.of("value", 3)).getResult());
                });
    }

    @Test
    void invokesAnnotatedTargetMethodThroughJdkProxy() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MosikaUdfAutoConfiguration.class))
                .withUserConfiguration(JdkProxyTestConfiguration.class)
                .run(context -> {
                    List<UdfDefinition> definitions = context.getBean(SpringUdfLoader.class).loadUdfs();

                    assertEquals(1, definitions.size());
                    assertTrue(AopUtils.isJdkDynamicProxy(context.getBean("jdkProxiedUdf")));
                    UdfDelegate<?, ?> delegate = UdfDelegate.of(definitions.getFirst().getUdf());
                    assertEquals(JdkProxiedUdf.class, delegate.type());
                    assertEquals("通过 JDK 代理执行", delegate.methods().getFirst().getDescription());
                    assertEquals(Integer.class,
                            delegate.methods().getFirst().getParameterTypes()[0]);

                    RuleSuite suite = new RuleSuite(
                            List.of(new RuleDefinition("proxy", "proxy.increment($.value)", "proxy")),
                            definitions);
                    assertEquals(12, suite.eval("proxy", java.util.Map.of("value", 1)).getResult());
                });
    }

    private static UdfDefinition definition(List<UdfDefinition> definitions, String fullName) {
        return definitions.stream()
                .filter(definition -> fullName(definition).equals(fullName))
                .findFirst()
                .orElseThrow();
    }

    private static String fullName(UdfDefinition definition) {
        return definition.getGroup().isEmpty()
                ? definition.getName()
                : definition.getGroup() + "." + definition.getName();
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        UdfDependency udfDependency() {
            return new UdfDependency();
        }

        @Bean("customConfiguredUdf")
        ConfiguredUdf configuredUdf() {
            return new ConfiguredUdf();
        }

        @Bean
        static BeanPostProcessor namedUdfProxy() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof NamedUdf) {
                        ProxyFactory proxyFactory = new ProxyFactory(bean);
                        proxyFactory.setProxyTargetClass(true);
                        return proxyFactory.getProxy();
                    }
                    return bean;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ExternalTestConfiguration {

        @Bean
        UdfDependency udfDependency() {
            return new UdfDependency();
        }

        @Bean
        ExternalConfiguredUdf externalConfiguredUdf(UdfDependency dependency) {
            return new ExternalConfiguredUdf(dependency);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdkProxyTestConfiguration {

        @Bean
        JdkProxiedUdf jdkProxiedUdf() {
            return new JdkProxiedUdf();
        }

        @Bean
        static BeanPostProcessor jdkUdfProxy() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof JdkProxiedUdf) {
                        ProxyFactory proxyFactory = new ProxyFactory(bean);
                        proxyFactory.addAdvice((MethodInterceptor) invocation ->
                                (Integer) invocation.proceed() + 10);
                        return proxyFactory.getProxy();
                    }
                    return bean;
                }
            };
        }
    }

    @Udf(group = "proxy", value = "increment")
    static class JdkProxiedUdf implements Functions.Function1<Integer, Integer> {

        @Override
        @UdfMethod(description = "通过 JDK 代理执行")
        public Integer apply(Integer value) {
            return value + 1;
        }
    }
}
