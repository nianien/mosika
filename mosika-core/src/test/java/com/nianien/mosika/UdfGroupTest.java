package com.nianien.mosika;

import com.nianien.mosika.annotation.Udf;
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
        public Object apply(Function<Object[], Object> function, Object... arguments) {
            return function.apply(arguments);
        }
    }

    public static class SumUdf implements Functions.Function2<Integer, Integer, Integer> {

        @Override
        public Integer apply(Integer left, Integer right) {
            return left + right;
        }
    }

    @Test
    public void testPlainObjectMemberMethodIsCallable() {
        RuleEngine engine = RuleEngine.builder()
                .udfDefinition(new UdfDefinition("bean", "helper", new PlainBeanUdf()))
                .build();

        // 普通对象没有 apply，仍可注册，并支持通过成员方法调用
        assertEquals("hi jack", engine.evalExpr("bean.helper.greet('jack')", null, null));
        assertEquals(5, engine.evalExpr("bean.helper.add(2, 3)", null, null));

        // 直接以函数方式执行才因缺少兼容 apply 报错
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
        // udf.someMethod(...) 调用原始对象的成员方法
        assertEquals(6, engine.evalExpr("math.calc.multiply(2, 3)", null, null));
    }

    /** 没有 apply 的普通对象，仅暴露成员方法 */
    public static class PlainBeanUdf {

        public String greet(String name) {
            return "hi " + name;
        }

        public int add(int left, int right) {
            return left + right;
        }
    }

    /** 同一对象既可作为函数执行（apply），又可访问其它成员方法 */
    public static class DualUdf implements Functions.Function2<Integer, Integer, Integer> {

        @Override
        public Integer apply(Integer left, Integer right) {
            return left + right;
        }

        public int multiply(int left, int right) {
            return left * right;
        }
    }

}
