package com.nianien.mosika;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;
import com.nianien.mosika.bean.User;
import com.nianien.mosika.bean.User.Contact;
import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.engine.RuleEngine;
import com.nianien.mosika.udf.UdfDefinition;
import com.nianien.mosika.eval.node.RuleNode;
import com.nianien.mosika.eval.parser.NodeBuilder;
import com.nianien.mosika.eval.result.NodeResult;
import com.nianien.mosika.suite.RuleSuite;
import com.nianien.mosika.udf.*;
import com.nianien.mosika.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Created on 2022-08-26
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Udf
public class UdfGroupTest {

    private final NodeBuilder nodeBuilder = new NodeBuilder();


    @Test
    public void testSayHello() {
        RuleEngine engine = RuleEngine.builder()
                .ruleDefinitions(Arrays.asList(
                        new RuleDefinition("1", "policy.sys.replace($)", "sys say"),
                        new RuleDefinition("2", "policy.sys.replace($)", "sys say")
                ))
                .udfDefinitions(Arrays.asList(
                        new UdfDefinition("policy.sys", "replace", new SayHelloUdf())
                )).build();

        String arg = "China";
        RuleNode decisionNode = nodeBuilder.build("1");
        NodeResult eval = new RuleSuite(engine).eval(decisionNode, arg);
        System.out.println(eval);
        assertEquals(eval.toString(),
                "NodeResult(expr=1, result=China, details=[RuleResult(expr=1,result=China,desc='sys say')])");
    }

    @Test
    public void testAutoCastUdf() {
        RuleEngine engine = RuleEngine.builder()
                .ruleDefinitions(Arrays.asList(
                        new RuleDefinition("1", "sys.user($)", "name of user"),
                        new RuleDefinition("2", "sys.user($,'robbin')", "name of user")
                ))
                .udfDefinitions(Arrays.asList(
                        new UdfDefinition("sys", "user", new AutoCastUdf())
                )).build();
        User user = new User("jack.ma", 18, new Contact("11111@ks.com", "1111"));
        String json = JsonUtils.toJson(user);
        Map map = JsonUtils.toMap(json, String.class, Object.class);
        System.out.println(map);
        {
            NodeResult result = new RuleSuite(engine).eval(nodeBuilder.build("1"), map);
            System.out.println(result);
        }
        {
            NodeResult result = new RuleSuite(engine).eval(nodeBuilder.build("2"), map);
            System.out.println(result);
        }

    }

    @Test
    public void testSayHelloWithGeneric() {
        RuleEngine engine = RuleEngine.builder()
                .ruleDefinitions(Arrays.asList(
                        new RuleDefinition("1", "sys.sayHello($, {\"river\":\"ChangJiang\"})", "sys.helloWithGeneric")
                )).udfDefinitions(Arrays.asList(
                        new UdfDefinition("sys", "sayHello", new HelloWithGenericUdf())
                )).build();
        String arg = "China";
        RuleNode decisionNode = nodeBuilder.build("1");
        NodeResult result = new RuleSuite(engine).eval(decisionNode, arg);
        System.out.println(result);
        assertEquals(result.toString(),
                "NodeResult(expr=1, result=This is China k:river v:ChangJiang;, " +
                        "details=[RuleResult(expr=1,result=This is China k:river v:ChangJiang;,desc='sys.helloWithGeneric')])");
    }


    @Test
    public void testSayHelloWithGeneric2() {
        RuleEngine engine = RuleEngine.builder()
                .ruleDefinitions(Arrays.asList(
                        new RuleDefinition("1", "sys.sayHello($, [\"qi\"], {\"river\":\"ChangJiang\"})",
                                "sys.helloWithGeneric")
                ))
                .udfDefinitions(Arrays.asList(
                        new UdfDefinition("sys", "sayHello", new HelloWithGenericUdf())
                ))
                .build();

        String arg = "China";
        NodeResult result = new RuleSuite(engine).eval(nodeBuilder.build("1"), arg);
        System.out.println(result);
        assertEquals("NodeResult(expr=1, result=This is China Outstanding person : [qi] k:river v:ChangJiang;, " +
                        "details=[RuleResult(expr=1,result=This is China Outstanding person : [qi] k:river v:ChangJiang;,desc='sys.helloWithGeneric')])",
                result.toString());
    }

    @Test
    public void testArgumentsCheck() {
        RuleEngine engine = RuleEngine.builder()
                .ruleDefinitions(Arrays.asList(
                        new RuleDefinition("1", "sys.checkArguments(\"11\", 11, $$, {\"river\":\"ChangJiang\"})",
                                "sys.checkArguments")
                ))
                .udfDefinitions(Arrays.asList(
                        new UdfDefinition("sys", "checkArguments", new CheckArgumentsUdf())
                )).build();
        String arg = "China";
        NodeResult result = new RuleSuite(engine).eval(nodeBuilder.build("1"), arg);
        System.out.println(result);
        assertEquals("NodeResult(expr=1, result=success, " +
                        "details=[RuleResult(expr=1,result=success,desc='sys.checkArguments')])",
                result.toString());
    }

    /**
     * 测试嵌套udf
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
     */
    @Test
    public void testNestedUdf() {
        RuleEngine.RuleEngineBuilder builder = RuleEngine.builder()
                .ruleDefinitions(Arrays.asList(
                        new RuleDefinition("1",
                                "policy.invokeUdf($, [\"qi\"], {\"river\":\"ChangJiang\"},sys.sayHello)",
                                "policy nestedHelloUdf")))
                .udfDefinitions(Arrays.asList(
                        new UdfDefinition("policy", "invokeUdf", new CallAnotherUdfUdf()),
                        new UdfDefinition("sys", "sayHello", new HelloWithGenericUdf())
                ));
        String arg = "China";
        NodeResult result = new RuleSuite(builder.build()).eval(nodeBuilder.build("1"), arg);
        System.out.println(result);
    }

    @Test
    public void testUdfAsJavaUdfArgument() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinitions(Arrays.asList(
                        new UdfDefinition("math", "jsSum3", "(a, b, c) => a + b + c"),
                        new UdfDefinition("math", "javaSum", new SumUdf()),
                        new UdfDefinition("policy", "invoke", new InvokeUdf())))
                .build();

        assertEquals(3, engine.evalExpr("policy.invoke(math.javaSum, 1, 2)", null, null));
        assertEquals(6, engine.evalExpr("policy.invoke(math.jsSum3, 1, 2, 3)", null, null));
    }

    public static class InvokeUdf
            implements Functions.Function2<Function<Object[], Object>, Object[], Object> {

        @Override
        @UdfMethod
        public Object apply(Function<Object[], Object> function, Object... arguments) {
            return function.apply(arguments);
        }
    }

    public static class SumUdf implements Functions.Function2<Integer, Integer, Integer> {

        @Override
        @UdfMethod
        public Integer apply(Integer left, Integer right) {
            return left + right;
        }
    }

    @Test
    public void testPlainObjectMemberMethodIsCallable() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinition(new UdfDefinition("bean", "helper", new PlainBeanUdf()))
                .build();

        // 普通对象没有 apply，仍可通过标注的方法调用
        assertEquals("hi jack", engine.evalExpr("bean.helper.greet('jack')", null, null));
        assertEquals(5, engine.evalExpr("bean.helper.add(2, 3)", null, null));
        assertEquals("undefined", engine.evalExpr("typeof bean.helper.hidden", null, null));
        assertEquals(true, engine.evalExpr(
                "bean.helper.greet === bean.helper.greet", null, null));
        assertEquals(false, engine.evalExpr(
                "bean.helper.greet === bean.helper.add", null, null));
        assertThrows(RuntimeException.class,
                () -> engine.evalExpr("bean.helper.greet.marker = 1", null, null));

        // 没有标注 apply 时不能直接调用
        assertThrows(RuntimeException.class,
                () -> engine.evalExpr("bean.helper(1)", null, null));
    }

    @Test
    public void testJavaUdfSupportsBothCallAndMemberAccess() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinition(new UdfDefinition("math", "calc", new DualUdf()))
                .build();

        // udf(...) 进入 UdfDelegate.apply()，按 apply 签名分派
        assertEquals(5, engine.evalExpr("math.calc(2, 3)", null, null));
        // udf.someMethod(...) 进入同一个代理调用具名方法
        assertEquals(6, engine.evalExpr("math.calc.multiply(2, 3)", null, null));
    }

    @Test
    public void testDelegateExposesOnlyAnnotatedMethodsAndMetadata() {
        UdfDelegate<?, ?> delegate = UdfDelegate.of(new PlainBeanUdf());

        assertEquals(2, delegate.methods().size());
        assertEquals("add", delegate.methods().getFirst().getName());
        assertEquals("计算两个整数之和", delegate.methods().getFirst().getDescription());
        assertEquals(5, delegate.invoke("add", 2, 3));
        assertFalse(delegate.contains("hidden"));
        assertThrows(RuntimeException.class, () -> delegate.invoke("hidden", "value"));
    }

    @Test
    public void testDelegateRejectsSameNameAndArity() {
        assertThrows(IllegalArgumentException.class,
                () -> UdfDelegate.of(new AmbiguousUdf()));
    }

    @Test
    public void testFunctionInterfaceDoesNotExposeUnannotatedApply() {
        UdfDelegate<?, ?> delegate = UdfDelegate.of(new UnexposedUdf());

        assertFalse(delegate.contains("apply"));
        assertThrows(RuntimeException.class, () -> delegate.invoke("apply", 1));
    }

    @Test
    public void testJavaUdfSupportsFixedAndVarArgsMethods() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinition(new UdfDefinition("mixed", new MixedArityUdf()))
                .build();

        assertEquals("fixed:a,b,c", engine.evalExpr("mixed('a', 'b', 'c')", null, null));
        assertEquals("varargs:a,b", engine.evalExpr("mixed('a', 'b')", null, null));
    }

    @Test
    public void testNamedOverloadsShareBoundFunction() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinition(new UdfDefinition("bean", "formatter", new NamedOverloadUdf()))
                .build();

        assertEquals(true, engine.evalExpr(
                "bean.formatter.format === bean.formatter.format", null, null));
        assertEquals("one:a", engine.evalExpr(
                "bean.formatter.format('a')", null, null));
        assertEquals("two:a,b", engine.evalExpr(
                "bean.formatter.format('a', 'b')", null, null));
    }

    /** 没有 apply 的普通对象，仅暴露成员方法 */
    public static class PlainBeanUdf {

        @UdfMethod(description = "向指定名称问候")
        public String greet(String name) {
            return "hi " + name;
        }

        @UdfMethod(description = "计算两个整数之和")
        public int add(int left, int right) {
            return left + right;
        }

        public String hidden(String value) {
            return value;
        }
    }

    /** 同一对象既可作为函数执行（apply），又可访问其它成员方法 */
    public static class DualUdf implements Functions.Function2<Integer, Integer, Integer> {

        @Override
        @UdfMethod
        public Integer apply(Integer left, Integer right) {
            return left + right;
        }

        @UdfMethod(description = "计算两个整数之积")
        public int multiply(int left, int right) {
            return left * right;
        }
    }

    public static class MixedArityUdf {

        @UdfMethod
        public String apply(String first, String second, String third) {
            return "fixed:" + first + "," + second + "," + third;
        }

        @UdfMethod
        public String apply(String first, String... values) {
            return "varargs:" + first + "," + String.join(",", values);
        }
    }

    public static class NamedOverloadUdf {

        @UdfMethod
        public String format(String value) {
            return "one:" + value;
        }

        @UdfMethod
        public String format(String first, String second) {
            return "two:" + first + "," + second;
        }
    }

    public static class AmbiguousUdf {

        @UdfMethod
        public String choose(String value) {
            return value;
        }

        @UdfMethod
        public Integer choose(Integer value) {
            return value;
        }
    }

    public static class UnexposedUdf implements Functions.Function1<Integer, Integer> {

        @Override
        public Integer apply(Integer value) {
            return value;
        }
    }

}
