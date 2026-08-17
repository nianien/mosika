package com.nianien.mosika.ui.tree;

import com.nianien.mosika.ui.tree.node.TreeNode;
import com.nianien.mosika.ui.tree.node.define.UINode;
import com.nianien.mosika.ui.tree.node.flow.ANode;
import com.nianien.mosika.ui.tree.node.flow.CNode;
import com.nianien.mosika.ui.tree.node.flow.SNode;
import com.nianien.mosika.ui.tree.node.rule.BNode;
import com.nianien.mosika.ui.tree.node.rule.RNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RuleArgsTemplateTest {

    @Test
    public void testAtomicRuleExprWithoutArgsUnchanged() {
        RNode rule = new RNode("r1");
        assertEquals("r1", rule.ruleExpr());

        rule.setArgs("   ");
        assertEquals("r1", rule.ruleExpr());
    }

    @Test
    public void testAtomicRuleExprWithArgs() {
        RNode rule = new RNode("r1");
        rule.setArgs("{\"limit\":100}");

        assertEquals("r1(\"\"\"{\"limit\":100}\"\"\")", rule.ruleExpr());
    }

    @Test
    public void testBooleanRuleAppliesNegationToArguments() {
        BNode rule = new BNode("c1");
        rule.setArgs("{\"limit\":100}");
        rule.setNegative(true);

        assertEquals("!c1(\"\"\"{\"limit\":100}\"\"\")", rule.ruleExpr());
    }

    @Test
    public void testActionAndConditionCompileBoundRuleArguments() {
        RNode actionRule = new RNode("a1");
        actionRule.setArgs("{\"step\":1}");
        ANode action = new ANode();
        action.setRule(actionRule);

        BNode conditionRule = new BNode("c1");
        conditionRule.setArgs("{\"limit\":100}");
        CNode condition = new CNode();
        condition.setRule(conditionRule);
        condition.setNext(action);

        assertEquals("c1(\"\"\"{\"limit\":100}\"\"\")?a1(\"\"\"{\"step\":1}\"\"\")",
                tree(condition).toRule().expr());
    }

    @Test
    public void testArgsAndNamesSurviveJsonRoundTrip() {
        ANode first = action("a1", "{\"step\":1}");
        first.setName("第一步");
        ANode second = action("a2", "{\"step\":2}");
        second.setName("第二步");

        SNode serial = new SNode();
        serial.setName("串行动作");
        serial.addBranch(first).addBranch(second);
        TreeNode tree = tree(serial);

        TreeNode restored = TreeNode.fromJson(tree.toJson());
        assertEquals(tree.toJson(), restored.toJson());
        assertEquals(tree.toRule().expr(), restored.toRule().expr());
    }

    private ANode action(String expression, String args) {
        RNode rule = new RNode(expression);
        rule.setArgs(args);
        ANode action = new ANode();
        action.setRule(rule);
        return action;
    }

    private TreeNode tree(UINode root) {
        TreeNode tree = new TreeNode();
        tree.setNext(root);
        return tree;
    }
}
