package com.nianien.mosika.ui.tree;

import com.nianien.mosika.ui.tree.node.TreeNode;
import com.nianien.mosika.ui.tree.node.define.UINode;
import com.nianien.mosika.ui.tree.node.flow.ActionNode;
import com.nianien.mosika.ui.tree.node.flow.ConditionNode;
import com.nianien.mosika.ui.tree.node.flow.SerialNode;
import com.nianien.mosika.ui.tree.node.rule.BooleanNode;
import com.nianien.mosika.ui.tree.node.rule.AtomicNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RuleArgsTemplateTest {

    @Test
    public void testAtomicRuleExprWithoutArgsUnchanged() {
        AtomicNode rule = new AtomicNode("r1");
        assertEquals("r1", rule.ruleExpr());

        rule.setArgs("   ");
        assertEquals("r1", rule.ruleExpr());
    }

    @Test
    public void testAtomicRuleExprWithArgs() {
        AtomicNode rule = new AtomicNode("r1");
        rule.setArgs("{\"limit\":100}");

        assertEquals("r1(\"\"\"{\"limit\":100}\"\"\")", rule.ruleExpr());
    }

    @Test
    public void testBooleanRuleAppliesNegationToArguments() {
        BooleanNode rule = new BooleanNode("c1");
        rule.setArgs("{\"limit\":100}");
        rule.setNegative(true);

        assertEquals("!c1(\"\"\"{\"limit\":100}\"\"\")", rule.ruleExpr());
    }

    @Test
    public void testActionAndConditionCompileBoundRuleArguments() {
        AtomicNode actionRule = new AtomicNode("a1");
        actionRule.setArgs("{\"step\":1}");
        ActionNode action = new ActionNode();
        action.setRule(actionRule);

        BooleanNode conditionRule = new BooleanNode("c1");
        conditionRule.setArgs("{\"limit\":100}");
        ConditionNode condition = new ConditionNode();
        condition.setRule(conditionRule);
        condition.setNext(action);

        assertEquals("c1(\"\"\"{\"limit\":100}\"\"\")?a1(\"\"\"{\"step\":1}\"\"\")",
                tree(condition).toRule().expr());
    }

    @Test
    public void testArgsAndNamesSurviveJsonRoundTrip() {
        ActionNode first = action("a1", "{\"step\":1}");
        first.setName("第一步");
        ActionNode second = action("a2", "{\"step\":2}");
        second.setName("第二步");

        SerialNode serial = new SerialNode();
        serial.setName("串行动作");
        serial.addBranch(first).addBranch(second);
        TreeNode tree = tree(serial);

        TreeNode restored = TreeNode.fromJson(tree.toJson());
        assertEquals(tree.toJson(), restored.toJson());
        assertEquals(tree.toRule().expr(), restored.toRule().expr());
    }

    private ActionNode action(String expression, String args) {
        AtomicNode rule = new AtomicNode(expression);
        rule.setArgs(args);
        ActionNode action = new ActionNode();
        action.setRule(rule);
        return action;
    }

    private TreeNode tree(UINode root) {
        TreeNode tree = new TreeNode();
        tree.setNext(root);
        return tree;
    }
}
