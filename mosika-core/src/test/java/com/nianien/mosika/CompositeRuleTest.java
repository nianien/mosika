package com.nianien.mosika;

import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.eval.node.RuleNode;
import com.nianien.mosika.eval.parser.NodeBuilder;
import com.nianien.mosika.mock.SimpleRuleLoader;
import com.nianien.mosika.suite.RuleSuite;
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

    private NodeBuilder builder(Map<String, String> compositeRules) {
        return new NodeBuilder(compositeRules.entrySet().stream()
                .map(entry -> new RuleDefinition(entry.getKey(), entry.getValue(), "",
                        RuleDefinition.RULE_TYPE_COMPOSITE))
                .toList());
    }


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
        NodeBuilder builder = builder(compositeRules);
        RuleNode node = builder.build("a");
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
        assertThrows(IllegalStateException.class, () -> builder(compositeRules));


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
        RuleSuite ruleSuite = simpleRuleLoader.loadSuite();
        String res1 = ruleSuite.eval("1002->1001&&1003", null).toString();
        System.out.println(res1);
    }
}
