package com.skyfalling.mosika;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.eval.parser.NodeBuilder;
import com.skyfalling.mosika.eval.parser.NodeGenerator;
import com.skyfalling.mosika.mock.SimpleRuleLoader;
import com.skyfalling.mosika.suite.RuleEvaluator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Created on 2022/6/17
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class CompositeRuleTest {


    /**
     * 测试复合规则解析
     */
    @Test
    public void testParse() {
        Map<String, String> compositeRules = new HashMap<>();
        compositeRules.put("a", "1||b&&c");
        compositeRules.put("b", "2?3:4");
        compositeRules.put("c", "5?d");
        compositeRules.put("d", "4||b");
        NodeBuilder.setGenerator(NodeGenerator.create(compositeRules));
        RuleNode node = NodeBuilder.build("a");
        System.out.println(node);
        assertEquals("a[1||(b[2?3:4]&&c[5?d[4||b[2?3:4]]])]", node.toString());

    }

    /**
     * 测试复合规则解析
     */
    @Test
    public void testCircleDependency() {

        Map<String, String> compositeRules = new HashMap<>();
        compositeRules.put("a", "1||b||c");
        compositeRules.put("b", "2&&c");
        compositeRules.put("c", "3||d");
        compositeRules.put("d", "4||b");
        NodeBuilder.setGenerator(NodeGenerator.create(compositeRules));
        assertThrows(IllegalStateException.class, () -> {
            try {
                NodeBuilder.build("a");
            } catch (Exception e) {
                e.printStackTrace();
                throw e.getCause();
            }
        });


    }

    @Test
    public void testEval() {
        SimpleRuleLoader simpleRuleLoader = new SimpleRuleLoader(
                Arrays.asList(
                        new RuleDefinition("1001", "true", "1001描述"),
                        new RuleDefinition("1002", "false", "1002描述"),
                        new RuleDefinition("1003", "1001?1002->1004", "1003描述", 2),
                        new RuleDefinition("1004", "false", "1004描述")
                ),
                Arrays.asList());
        RuleEvaluator ruleEvaluator = simpleRuleLoader.loadSuite().getRuleEvaluator();
        String res1 = ruleEvaluator.eval("1002->1001&&1003", null).toString();
        System.out.println(res1);
    }

    @AfterAll
    public static void tearDown() {
        NodeBuilder.setGenerator(NodeGenerator.create());
    }

}
