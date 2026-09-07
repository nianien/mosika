package com.nianien.mosika.ui.tree;

import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.eval.node.RuleNode;
import com.nianien.mosika.eval.parser.NodeBuilder;
import com.nianien.mosika.ui.tree.node.TreeNode;
import com.nianien.mosika.ui.tree.node.define.NameNode;
import com.nianien.mosika.ui.tree.node.define.UINode;
import com.nianien.mosika.ui.tree.node.flow.ActionNode;
import com.nianien.mosika.ui.tree.node.flow.ConditionNode;
import com.nianien.mosika.ui.tree.node.flow.DecisionNode;
import com.nianien.mosika.ui.tree.node.flow.LoopNode;
import com.nianien.mosika.ui.tree.node.flow.ParallelNode;
import com.nianien.mosika.ui.tree.node.flow.SerialNode;
import com.nianien.mosika.ui.tree.node.rule.BooleanNode;
import com.nianien.mosika.ui.tree.node.rule.MatchNode;
import com.nianien.mosika.ui.tree.node.rule.LogicNode;
import com.nianien.mosika.ui.tree.node.rule.AtomicNode;
import com.nianien.mosika.ui.tree.visitor.TreeVisitor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TreeNodeTest {

    @Test
    public void testTreeRootIsIndependentFromExecutionNodes() {
        TreeNode tree = new TreeNode();

        assertEquals(NameNode.class, TreeNode.class.getSuperclass());
        ActionNode emptyAction = assertInstanceOf(ActionNode.class, tree.getNext());
        assertEquals("∅", emptyAction.getRule().getExpr());
    }

    @Test
    public void testNaturalActionChainUsesFlowNext() {
        TreeNode tree = tree(actions("a1", "a2", "a3"));

        String json = tree.toJson();
        assertTrue(json.contains("\"type\":\"TreeNode\""));
        assertTrue(json.contains("\"type\":\"ActionNode\""));
        assertTrue(json.contains("\"type\":\"AtomicNode\""));
        assertFalse(json.contains("\"type\":\"J\""));
        assertEquals("a1->a2->a3", tree.toRule().expr());
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testLegacyTypeIdsAreRejected() {
        assertThrows(IllegalStateException.class,
                () -> TreeNode.fromJson("{\"type\":\"T\"}"));
    }

    @Test
    public void testConditionNextIsMatchedBranch() {
        BooleanNode rule = b("c1");
        rule.setNegative(true);
        ConditionNode condition = c(rule, actions("a1", "a2"));
        TreeNode tree = tree(condition);

        assertEquals("!c1?(a1->a2)", tree.toRule().expr());
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testDecisionUsesOrderedConditionBranchesAndDefaultBranch() {
        DecisionNode decision = new DecisionNode();
        decision.addBranch(c(b("c1"), a("a1")));
        decision.addBranch(c(b("c2"), a("a2")));
        decision.setDefaultBranch(a("a3"));
        TreeNode tree = tree(decision);

        assertEquals("c1?a1:(c2?a2:a3)", tree.toRule().expr());
        assertEquals(Set.of("c1", "a1", "c2", "a2", "a3"), tree.collect());
        assertTrue(tree.toJson().contains("\"defaultBranch\""));
        assertFalse(tree.toJson().contains("\"action\""));
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testDecisionDefaultBranchSupportsExecutableSubtrees() {
        DecisionNode nested = new DecisionNode();
        nested.addBranch(c(b("c2"), a("a2")));
        nested.setDefaultBranch(a("a3"));

        ParallelNode parallel = new ParallelNode();
        parallel.addBranch(a("p1"));

        SerialNode serial = new SerialNode();
        serial.addBranch(a("s1"));

        LoopNode loop = loop(2, "retry", a("retryAction"));

        for (UINode defaultBranch : List.of(a("a1"), nested, loop, parallel, serial)) {
            DecisionNode decision = new DecisionNode();
            decision.addBranch(c(b("c1"), a("matched")));
            decision.setDefaultBranch(defaultBranch);

            tree(decision).validate();
            tree(decision).toRule();
        }
    }

    @Test
    public void testSerialAndParallelOwnCompleteSubtrees() {
        SerialNode serial = new SerialNode();
        serial.addBranch(actions("a1", "a2"));
        serial.addBranch(c(b("c1"), a("a3")));

        assertEquals("∅->(a1->a2)->(c1?a3)", tree(serial).toRule().expr());

        ParallelNode parallel = new ParallelNode();
        parallel.addBranch(a("a1"));
        parallel.addBranch(actions("a2", "a3"));

        assertEquals("∅=>a1=>(a2->a3)", tree(parallel).toRule().expr());
        assertJsonAndRuleRoundTrip(tree(serial));
        assertJsonAndRuleRoundTrip(tree(parallel));
    }

    @Test
    public void testRuleTreeAndNamesSurviveJsonRoundTrip() {
        MatchNode hits = new MatchNode(1, 2);
        hits.setName("命中数量条件");
        hits.addRule(b("c1")).addRule(b("c2")).addRule(b("c3"));

        ConditionNode condition = c(hits, a("a1"));
        condition.setName("风险判断");
        TreeNode tree = tree(condition);
        tree.setName("内容审核流程");

        TreeNode restored = TreeNode.fromJson(tree.toJson());
        ConditionNode restoredCondition = assertInstanceOf(ConditionNode.class, restored.getNext());
        MatchNode restoredRule = assertInstanceOf(MatchNode.class, restoredCondition.getRule());

        assertEquals("内容审核流程", restored.getName());
        assertEquals("风险判断", restoredCondition.getName());
        assertEquals("命中数量条件", restoredRule.getName());
        assertEquals("some(1,2,c1,c2,c3)?a1", restored.toRule().expr());
        assertJsonAndRuleRoundTrip(restored);
    }

    @Test
    public void testLoopSupportsJsonCompilationAndNesting() {
        LoopNode inner = loop(2, "innerCondition", a("innerAction"));
        LoopNode outer = loop(3, "outerCondition", inner);
        TreeNode tree = tree(outer);

        tree.validate();
        String json = tree.toJson();

        assertTrue(json.contains("\"type\":\"LoopNode\""));
        assertFalse(json.contains("\"condition\":"));
        assertFalse(json.contains("\"action\":"));
        assertEquals("loop(3,outerCondition,loop(2,innerCondition,innerAction))",
                tree.toRule().expr());
        assertEquals(Set.of("outerCondition", "innerCondition", "innerAction"),
                tree.collect());
        assertEquals(Set.of("outerCondition", "innerCondition"),
                tree.visit(TreeVisitor.CONDITION_RULE_ID_COLLECTOR,
                        new java.util.LinkedHashSet<>()));
        assertEquals(Set.of("innerAction"),
                tree.visit(TreeVisitor.ACTION_RULE_ID_COLLECTOR,
                        new java.util.LinkedHashSet<>()));
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testReferenceCollectorsRespectActionAndConditionDomains() {
        LogicNode conditionRule = LogicNode.and().addRule(b("c1")).addRule(b("c2"));
        SerialNode matched = new SerialNode();
        matched.addBranch(a("a1"));
        matched.addBranch(a("a2"));

        ActionNode root = a("a0");
        root.setNext(c(conditionRule, matched));
        TreeNode tree = tree(root);

        assertEquals(Set.of("a0", "c1", "c2", "a1", "a2"), tree.collect());
        assertEquals(Set.of("a0", "a1", "a2"),
                tree.visit(TreeVisitor.ACTION_RULE_ID_COLLECTOR, new java.util.LinkedHashSet<>()));
        assertEquals(Set.of("c1", "c2"),
                tree.visit(TreeVisitor.CONDITION_RULE_ID_COLLECTOR, new java.util.LinkedHashSet<>()));
    }

    @Test
    public void testStructuralValidation() {
        ActionNode missingRule = new ActionNode();
        assertThrows(IllegalStateException.class, tree(missingRule)::validate);

        assertThrows(IllegalStateException.class, tree(new SerialNode())::validate);
        assertThrows(IllegalStateException.class, tree(new ParallelNode())::validate);

        DecisionNode decision = new DecisionNode();
        decision.addBranch(c(b("c1"), a("a1")));
        assertThrows(IllegalStateException.class, tree(decision)::validate);
        assertThrows(IllegalArgumentException.class, tree(decision)::toRule);

        decision.setDefaultBranch(c(b("c2"), a("a2")));
        assertThrows(IllegalStateException.class, tree(decision)::validate);

        LogicNode logic = LogicNode.and().addRule(b("c1"));
        assertThrows(IllegalStateException.class, tree(c(logic, null))::validate);

        MatchNode hits = new MatchNode(3, 3);
        hits.addRule(b("c1")).addRule(b("c2"));
        assertThrows(IllegalStateException.class, tree(c(hits, null))::validate);

        LoopNode negativeMax = loop(-1, "condition", a("action"));
        assertThrows(IllegalStateException.class, tree(negativeMax)::validate);

        LoopNode missingCondition = new LoopNode();
        missingCondition.setNext(a("action"));
        assertThrows(IllegalStateException.class, tree(missingCondition)::validate);

        LoopNode missingNext = new LoopNode();
        missingNext.setRule(b("condition"));
        assertThrows(IllegalStateException.class, tree(missingNext)::validate);
    }

    @Test
    public void testIterativeTreeSizeValidation() {
        ActionNode root = actions("a0");
        ActionNode tail = root;
        for (int i = 1; i < 128; i++) {
            ActionNode next = a("a" + i);
            tail.setNext(next);
            tail = next;
        }
        assertThrows(IllegalStateException.class, () -> tree(root).validateSize(64, 1000));

        ActionNode shared = a("shared");
        SerialNode serial = new SerialNode();
        serial.addBranch(shared).addBranch(shared);
        assertThrows(IllegalStateException.class, () -> tree(serial).validateSize(128, 1000));

        DecisionNode repeatedDefault = new DecisionNode();
        repeatedDefault.addBranch(c(b("c1"), shared));
        repeatedDefault.setDefaultBranch(shared);
        assertThrows(IllegalStateException.class, () -> tree(repeatedDefault).validateSize(128, 1000));

        LoopNode cyclicLoop = loop(1, "condition", a("action"));
        cyclicLoop.setNext(cyclicLoop);
        assertThrows(IllegalStateException.class,
                () -> tree(cyclicLoop).validateSize(128, 1000));

        SerialNode many = new SerialNode();
        for (int i = 0; i < 20; i++) {
            many.addBranch(a("a" + i));
        }
        TreeNode wide = tree(many);
        wide.validateSize(128, 64);
        assertThrows(IllegalStateException.class, () -> wide.validateSize(128, 32));
    }

    @Test
    public void testUiCompilationUsesIndependentNodeBuilder() {
        NodeBuilder compositeBuilder = new NodeBuilder(List.of(new RuleDefinition(
                "c1", "c2&&c3", "", RuleDefinition.RULE_TYPE_COMPOSITE)));

        assertEquals("c1[c2&&c3]", compositeBuilder.build("c1").toString());
        assertEquals("c1", tree(c(b("c1"), null)).toRule().expr());
    }

    private void assertJsonAndRuleRoundTrip(TreeNode tree) {
        String json = tree.toJson();
        RuleNode rule = tree.toRule();
        TreeNode restored = TreeNode.fromJson(json);
        assertEquals(json, restored.toJson());
        assertEquals(rule.expr(), restored.toRule().expr());
    }

    private TreeNode tree(UINode root) {
        TreeNode tree = new TreeNode();
        tree.setNext(root);
        return tree;
    }

    private ActionNode a(String expression) {
        ActionNode action = new ActionNode();
        action.setRule(new AtomicNode(expression));
        return action;
    }

    private ActionNode actions(String... expressions) {
        ActionNode head = null;
        ActionNode tail = null;
        for (String expression : expressions) {
            ActionNode action = a(expression);
            if (head == null) {
                head = action;
            } else {
                tail.setNext(action);
            }
            tail = action;
        }
        return head;
    }

    private BooleanNode b(String expression) {
        return new BooleanNode(expression);
    }

    private ConditionNode c(BooleanNode rule, UINode matched) {
        ConditionNode condition = new ConditionNode();
        condition.setRule(rule);
        condition.setNext(matched);
        return condition;
    }

    private LoopNode loop(int max, String condition, UINode action) {
        LoopNode loop = new LoopNode();
        loop.setMax(max);
        loop.setRule(b(condition));
        loop.setNext(action);
        return loop;
    }
}
