package com.nianien.mosika.ui.tree;

import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.eval.node.RuleNode;
import com.nianien.mosika.eval.parser.NodeBuilder;
import com.nianien.mosika.ui.tree.node.TreeNode;
import com.nianien.mosika.ui.tree.node.define.NameNode;
import com.nianien.mosika.ui.tree.node.define.UINode;
import com.nianien.mosika.ui.tree.node.flow.ANode;
import com.nianien.mosika.ui.tree.node.flow.CNode;
import com.nianien.mosika.ui.tree.node.flow.DNode;
import com.nianien.mosika.ui.tree.node.flow.PNode;
import com.nianien.mosika.ui.tree.node.flow.SNode;
import com.nianien.mosika.ui.tree.node.rule.BNode;
import com.nianien.mosika.ui.tree.node.rule.HNode;
import com.nianien.mosika.ui.tree.node.rule.LNode;
import com.nianien.mosika.ui.tree.node.rule.RNode;
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
        ANode emptyAction = assertInstanceOf(ANode.class, tree.getNext());
        assertEquals("∅", emptyAction.getRule().getExpr());
    }

    @Test
    public void testNaturalActionChainUsesFlowNext() {
        TreeNode tree = tree(actions("a1", "a2", "a3"));

        String json = tree.toJson();
        assertTrue(json.contains("\"type\":\"T\""));
        assertTrue(json.contains("\"type\":\"A\""));
        assertTrue(json.contains("\"type\":\"R\""));
        assertFalse(json.contains("\"type\":\"J\""));
        assertEquals("a1->a2->a3", tree.toRule().expr());
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testConditionNextIsMatchedBranch() {
        BNode rule = b("c1");
        rule.setNegative(true);
        CNode condition = c(rule, actions("a1", "a2"));
        TreeNode tree = tree(condition);

        assertEquals("!c1?(a1->a2)", tree.toRule().expr());
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testDecisionUsesOrderedConditionBranchesAndDefaultBranch() {
        DNode decision = new DNode();
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
        DNode nested = new DNode();
        nested.addBranch(c(b("c2"), a("a2")));
        nested.setDefaultBranch(a("a3"));

        PNode parallel = new PNode();
        parallel.addBranch(a("p1"));

        SNode serial = new SNode();
        serial.addBranch(a("s1"));

        for (UINode defaultBranch : List.of(a("a1"), nested, parallel, serial)) {
            DNode decision = new DNode();
            decision.addBranch(c(b("c1"), a("matched")));
            decision.setDefaultBranch(defaultBranch);

            tree(decision).validate();
            tree(decision).toRule();
        }
    }

    @Test
    public void testSerialAndParallelOwnCompleteSubtrees() {
        SNode serial = new SNode();
        serial.addBranch(actions("a1", "a2"));
        serial.addBranch(c(b("c1"), a("a3")));

        assertEquals("∅->(a1->a2)->(c1?a3)", tree(serial).toRule().expr());

        PNode parallel = new PNode();
        parallel.addBranch(a("a1"));
        parallel.addBranch(actions("a2", "a3"));

        assertEquals("∅=>a1=>(a2->a3)", tree(parallel).toRule().expr());
        assertJsonAndRuleRoundTrip(tree(serial));
        assertJsonAndRuleRoundTrip(tree(parallel));
    }

    @Test
    public void testRuleTreeAndNamesSurviveJsonRoundTrip() {
        HNode hits = new HNode(1, 2);
        hits.setName("命中数量条件");
        hits.addRule(b("c1")).addRule(b("c2")).addRule(b("c3"));

        CNode condition = c(hits, a("a1"));
        condition.setName("风险判断");
        TreeNode tree = tree(condition);
        tree.setName("内容审核流程");

        TreeNode restored = TreeNode.fromJson(tree.toJson());
        CNode restoredCondition = assertInstanceOf(CNode.class, restored.getNext());
        HNode restoredRule = assertInstanceOf(HNode.class, restoredCondition.getRule());

        assertEquals("内容审核流程", restored.getName());
        assertEquals("风险判断", restoredCondition.getName());
        assertEquals("命中数量条件", restoredRule.getName());
        assertEquals("some(1,2,c1,c2,c3)?a1", restored.toRule().expr());
        assertJsonAndRuleRoundTrip(restored);
    }

    @Test
    public void testReferenceCollectorsRespectActionAndConditionDomains() {
        LNode conditionRule = LNode.and().addRule(b("c1")).addRule(b("c2"));
        SNode matched = new SNode();
        matched.addBranch(a("a1"));
        matched.addBranch(a("a2"));

        ANode root = a("a0");
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
        ANode missingRule = new ANode();
        assertThrows(IllegalStateException.class, tree(missingRule)::validate);

        assertThrows(IllegalStateException.class, tree(new SNode())::validate);
        assertThrows(IllegalStateException.class, tree(new PNode())::validate);

        DNode decision = new DNode();
        decision.addBranch(c(b("c1"), a("a1")));
        assertThrows(IllegalStateException.class, tree(decision)::validate);
        assertThrows(IllegalArgumentException.class, tree(decision)::toRule);

        decision.setDefaultBranch(c(b("c2"), a("a2")));
        assertThrows(IllegalStateException.class, tree(decision)::validate);

        LNode logic = LNode.and().addRule(b("c1"));
        assertThrows(IllegalStateException.class, tree(c(logic, null))::validate);

        HNode hits = new HNode(3, 3);
        hits.addRule(b("c1")).addRule(b("c2"));
        assertThrows(IllegalStateException.class, tree(c(hits, null))::validate);
    }

    @Test
    public void testIterativeTreeSizeValidation() {
        ANode root = actions("a0");
        ANode tail = root;
        for (int i = 1; i < 128; i++) {
            ANode next = a("a" + i);
            tail.setNext(next);
            tail = next;
        }
        assertThrows(IllegalStateException.class, () -> tree(root).validateSize(64, 1000));

        ANode shared = a("shared");
        SNode serial = new SNode();
        serial.addBranch(shared).addBranch(shared);
        assertThrows(IllegalStateException.class, () -> tree(serial).validateSize(128, 1000));

        DNode repeatedDefault = new DNode();
        repeatedDefault.addBranch(c(b("c1"), shared));
        repeatedDefault.setDefaultBranch(shared);
        assertThrows(IllegalStateException.class, () -> tree(repeatedDefault).validateSize(128, 1000));

        SNode many = new SNode();
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

    private ANode a(String expression) {
        ANode action = new ANode();
        action.setRule(new RNode(expression));
        return action;
    }

    private ANode actions(String... expressions) {
        ANode head = null;
        ANode tail = null;
        for (String expression : expressions) {
            ANode action = a(expression);
            if (head == null) {
                head = action;
            } else {
                tail.setNext(action);
            }
            tail = action;
        }
        return head;
    }

    private BNode b(String expression) {
        return new BNode(expression);
    }

    private CNode c(BNode rule, UINode matched) {
        CNode condition = new CNode();
        condition.setRule(rule);
        condition.setNext(matched);
        return condition;
    }
}
