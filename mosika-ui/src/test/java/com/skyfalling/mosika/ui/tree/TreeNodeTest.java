package com.skyfalling.mosika.ui.tree;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.eval.parser.NodeBuilder;
import com.skyfalling.mosika.ui.tree.node.TreeNode;
import com.skyfalling.mosika.ui.tree.node.define.FlowNode;
import com.skyfalling.mosika.ui.tree.node.flow.ANode;
import com.skyfalling.mosika.ui.tree.node.flow.CNode;
import com.skyfalling.mosika.ui.tree.node.flow.DNode;
import com.skyfalling.mosika.ui.tree.node.flow.JNode;
import com.skyfalling.mosika.ui.tree.node.flow.PNode;
import com.skyfalling.mosika.ui.tree.node.flow.SNode;
import com.skyfalling.mosika.ui.tree.node.rule.HNode;
import com.skyfalling.mosika.ui.tree.node.rule.LNode;
import com.skyfalling.mosika.ui.tree.node.rule.RNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI树的JSON持久化、结构校验和单向规则编译测试。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class TreeNodeTest {

    @Test
    public void testHitsJsonRoundTrip() {
        HNode hits = new HNode(null, 2);
        hits.addRule(r("101")).addRule(r("102")).addRule(r("103"));
        JNode judge = j(hits, a("104"));
        DNode decision = new DNode();
        decision.addBranch(judge);
        decision.setAction(a("105"));
        TreeNode tree = tree(decision);

        String json = tree.toJson();
        assertTrue(json.contains("\"type\":\"H\""));
        assertTrue(json.contains("\"expr\":\"some\""));
        assertFalse(json.contains("\"minHits\""));
        assertTrue(json.contains("\"maxHits\":2"));
        assertJsonAndRuleRoundTrip(tree);
        assertEquals("some(_,2,101,102,103)?104:105", tree.toRule().expr());
    }

    @Test
    public void testTopLevelRuleTrees() {
        RNode negativeAtomic = r("c1");
        negativeAtomic.setNegative(true);
        assertTopLevelRule(negativeAtomic, "!c1");

        LNode logic = LNode.and().addRule(r("c1")).addRule(r("c2"));
        logic.setNegative(true);
        assertTopLevelRule(logic, "!(c1&&c2)");

        assertTopLevelRule(
                LNode.and().addRule(r("c1")).addRule(r("c2")).addRule(r("c3")),
                "(c1&&c2&&c3)", "c1&&c2&&c3");
        assertTopLevelRule(
                LNode.or().addRule(r("c1")).addRule(r("c2")).addRule(r("c3")),
                "(c1||c2||c3)", "c1||c2||c3");

        HNode hits = new HNode(1, 2);
        hits.addRule(r("c1")).addRule(r("c2")).addRule(r("c3"));
        hits.setNegative(true);
        assertTopLevelRule(hits, "!some(1,2,c1,c2,c3)");
    }

    @Test
    public void testRuleNodeNameJsonRoundTrip() {
        SNode serial = new SNode();

        RNode atomicRule = r("c1");
        atomicRule.setName("单一规则名称");
        serial.addBranch(j(atomicRule, null));

        LNode compositeRule = LNode.and().addRule(r("c2")).addRule(r("c3"));
        compositeRule.setName("复合规则名称");
        serial.addBranch(j(compositeRule, null));

        TreeNode restored = TreeNode.fromJson(tree(serial).toJson());
        SNode restoredSerial = assertInstanceOf(SNode.class, restored.getNext());
        JNode restoredAtomic = assertInstanceOf(JNode.class, restoredSerial.getBranches().get(0));
        JNode restoredComposite = assertInstanceOf(JNode.class, restoredSerial.getBranches().get(1));

        assertEquals("单一规则名称", restoredAtomic.getRule().getName());
        assertEquals("复合规则名称", restoredComposite.getRule().getName());
        assertEquals("RNode", restoredAtomic.getRule().getLabel());
        assertJsonAndRuleRoundTrip(restored);
    }

    @Test
    public void testCollectAndValidate() {
        SNode serial = new SNode();
        serial.addBranch(a("a0"));

        HNode hits = new HNode(1, 2);
        hits.addRule(r("r1")).addRule(r("r_2"));
        DNode decision = new DNode();
        decision.addBranch(j(hits, a("a1")));
        decision.setAction(a("a2"));
        serial.addBranch(j(r("c1"), decision));
        serial.addBranch(a("a3"));

        TreeNode tree = tree(serial);
        tree.validate();
        assertEquals(Set.of("a0", "c1", "r1", "r_2", "a1", "a2", "a3"), tree.collect());

        HNode invalidHits = new HNode(3, 3);
        invalidHits.addRule(r("r1")).addRule(r("r2"));
        assertThrows(IllegalStateException.class, tree(j(invalidHits, null))::validate);

        DNode redundantDecision = new DNode();
        redundantDecision.addBranch(c("c1", "a1"));
        TreeNode redundantTree = tree(redundantDecision);
        assertThrows(IllegalStateException.class, redundantTree::validate);
        assertThrows(IllegalArgumentException.class, redundantTree::toRule);
    }

    @Test
    public void testStructuralMinimumCardinality() {
        assertThrows(IllegalStateException.class, tree(new SNode())::validate);
        assertThrows(IllegalStateException.class, tree(new PNode())::validate);
        assertThrows(IllegalStateException.class,
                tree(j(LNode.and().addRule(r("r1")), null))::validate);

        JNode missingRule = new JNode();
        missingRule.setRule(null);
        assertThrows(IllegalStateException.class, tree(missingRule)::validate);
    }

    @Test
    public void testActionChainJsonRoundTrip() {
        ANode a1 = a("a1");
        ANode a2 = a("a2");
        CNode c3 = c("c3");
        ANode a3 = a("a3");
        ANode a4 = a("a4");
        a1.setNext(a2);
        a2.setNext(c3);
        c3.setAction(a3);
        a3.setNext(a4);
        TreeNode tree = tree(a1);

        String json = tree.toJson();
        assertTrue(json.contains("\"label\":\"TreeNode\""));
        assertTrue(json.contains("\"label\":\"ANode\""));
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testCompositeReferenceUsesOrdinaryActionNode() {
        ANode reference = a("flow_child_flow");
        reference.setNext(a("a1"));
        ANode before = a("a0");
        before.setNext(reference);
        TreeNode tree = tree(before);

        String json = tree.toJson();
        assertTrue(json.contains("\"type\":\"A\""));
        TreeNode restored = TreeNode.fromJson(json);
        ANode restoredReference = assertInstanceOf(ANode.class,
                assertInstanceOf(ANode.class, restored.getNext()).getNext());

        assertEquals("flow_child_flow", restoredReference.getExpr());
        assertEquals("a0->flow_child_flow->a1", restored.toRule().expr());
        assertEquals(Set.of("a0", "flow_child_flow", "a1"), restored.collect());
        assertJsonAndRuleRoundTrip(restored);
    }

    @Test
    public void testDecisionJsonRoundTrip() {
        ANode a0 = a("a0");
        DNode decision = new DNode();
        a0.setNext(decision);
        decision.addBranch(c("c1", "a1"));
        decision.addBranch(c("c2", "a2"));
        decision.setAction(a("a3"));
        TreeNode tree = tree(a0);

        TreeNode restored = TreeNode.fromJson(tree.toJson());
        DNode restoredDecision = assertInstanceOf(DNode.class,
                assertInstanceOf(ANode.class, restored.getNext()).getNext());
        restoredDecision.getBranches().forEach(branch -> assertInstanceOf(CNode.class, branch));
        assertEquals(tree.toJson(), restored.toJson());
        assertEquals(tree.toRule().expr(), restored.toRule().expr());
    }

    @Test
    public void testDecisionAllowsBranchWithoutAction() {
        DNode decision = new DNode();
        decision.addBranch(c("c1"));
        decision.addBranch(c("c2", "a2"));
        TreeNode tree = tree(decision);

        tree.validate();
        assertEquals("c1?∅:(c2?a2)", tree.toRule().expr());
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testSerialJsonRoundTrip() {
        SNode serial = new SNode();
        serial.addBranch(c("c1", "a1"));
        serial.addBranch(c("c2", "a2"));
        serial.addBranch(c("c3", "a3"));
        TreeNode tree = tree(serial);

        assertEquals("∅->(c1?a1)->(c2?a2)->(c3?a3)", tree.toRule().expr());
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testNestedActionChainsInSerialNode() {
        SNode serial = new SNode();
        ANode a1 = a("a1");
        a1.setNext(a("a2"));
        serial.addBranch(a1);
        ANode a3 = a("a3");
        a3.setNext(a("a4"));
        serial.addBranch(a3);
        TreeNode tree = tree(serial);

        assertEquals("∅->(a1->a2)->(a3->a4)", tree.toRule().expr());
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testParallelJsonRoundTrip() {
        PNode parallel = new PNode();
        parallel.addBranch(a("a1"));
        parallel.addBranch(a("a2"));
        parallel.addBranch(a("a3"));
        ANode a0 = a("a0");
        a0.setNext(parallel);
        TreeNode tree = tree(a0);

        assertEquals("a0->(∅=>a1=>a2=>a3)", tree.toRule().expr());
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testCompositeJudgeJsonRoundTrip() {
        LNode rule = LNode.and()
                .addRule(LNode.or().addRule(r("r1")).addRule(r("r2")))
                .addRule(LNode.or().addRule(r("r3")).addRule(r("r4")));
        JNode judge = j(rule, a("a1"));
        DNode decision = new DNode();
        decision.addBranch(judge);
        decision.addBranch(c("c2", "a2"));
        decision.setAction(a("a3"));
        TreeNode tree = tree(decision);

        TreeNode restored = TreeNode.fromJson(tree.toJson());
        DNode restoredDecision = assertInstanceOf(DNode.class, restored.getNext());
        assertInstanceOf(JNode.class, restoredDecision.getBranches().get(0));
        assertInstanceOf(CNode.class, restoredDecision.getBranches().get(1));
        assertJsonAndRuleRoundTrip(tree);
    }

    @Test
    public void testRecursiveFlowTreeJsonRoundTrip() {
        SNode root = new SNode();
        PNode parallel = new PNode();
        parallel.addBranch(a("a1"));
        parallel.addBranch(a("a2"));
        parallel.addBranch(a("a3"));
        root.addBranch(parallel);

        DNode decision = new DNode();
        SNode matchedFlow = new SNode();
        DNode nestedDecision = new DNode();
        nestedDecision.addBranch(c("c1", "a1"));
        nestedDecision.addBranch(j(LNode.and().addRule(r("c2")).addRule(r("c3")), a("a3")));
        matchedFlow.addBranch(nestedDecision);
        matchedFlow.addBranch(c("c4", "a4"));
        decision.addBranch(j(LNode.or()
                .addRule(r("c1"))
                .addRule(LNode.and().addRule(r("c2")).addRule(r("c3"))), matchedFlow));

        DNode secondFlow = new DNode();
        secondFlow.addBranch(c("c6", "a6"));
        secondFlow.addBranch(c("c7", "a7"));
        secondFlow.setAction(a("a5"));
        decision.addBranch(c("c5", secondFlow));

        SNode defaultFlow = new SNode();
        defaultFlow.addBranch(c("c8", "a8"));
        defaultFlow.addBranch(c("c9", "a9"));
        decision.setAction(defaultFlow);
        root.addBranch(decision);

        assertJsonAndRuleRoundTrip(tree(root));
    }

    @Test
    public void testIterativeTreeSizeValidation() {
        TreeNode deep = new TreeNode();
        ANode current = assertInstanceOf(ANode.class, deep.getNext());
        for (int i = 0; i < 128; i++) {
            ANode next = a("a" + i);
            current.setNext(next);
            current = next;
        }
        assertThrows(IllegalStateException.class, () -> deep.validateSize(128, 2000));

        TreeNode repeated = new TreeNode();
        SNode serial = new SNode();
        ANode shared = a("shared");
        serial.addBranch(shared).addBranch(shared);
        repeated.setNext(serial);
        assertThrows(IllegalStateException.class, () -> repeated.validateSize(128, 2000));

        TreeNode wide = new TreeNode();
        SNode many = new SNode();
        for (int i = 0; i < 20; i++) {
            many.addBranch(a("a" + i));
        }
        wide.setNext(many);
        wide.validateSize(128, 32);
        assertThrows(IllegalStateException.class, () -> wide.validateSize(128, 16));
    }

    @Test
    public void testUiCompilationUsesIndependentNodeBuilder() {
        NodeBuilder compositeBuilder = new NodeBuilder(List.of(new RuleDefinition(
                "c1", "c2&&c3", "", RuleDefinition.RULE_TYPE_COMPOSITE)));

        assertEquals("c1[c2&&c3]", compositeBuilder.build("c1").toString());
        assertEquals("c1", tree(j(r("c1"), null)).toRule().expr());
    }

    private void assertTopLevelRule(RNode rule, String expression) {
        assertTopLevelRule(rule, expression, expression);
    }

    private void assertTopLevelRule(RNode rule, String uiExpression, String compiledExpression) {
        TreeNode tree = tree(j(rule, null));
        assertEquals(uiExpression, rule.ruleExpr());
        assertEquals(compiledExpression, tree.toRule().expr());
        assertJsonAndRuleRoundTrip(tree);
    }

    private void assertJsonAndRuleRoundTrip(TreeNode tree) {
        String json = tree.toJson();
        RuleNode rule = tree.toRule();
        TreeNode restored = TreeNode.fromJson(json);
        assertEquals(json, restored.toJson());
        assertEquals(rule.expr(), restored.toRule().expr());
    }

    private TreeNode tree(FlowNode node) {
        TreeNode tree = new TreeNode();
        tree.setNext(node);
        return tree;
    }

    private JNode j(RNode rule, FlowNode action) {
        JNode judge = new JNode();
        judge.setRule(rule);
        judge.setAction(action);
        return judge;
    }

    private CNode c(String condition, String action) {
        return c(condition, a(action));
    }

    private CNode c(String condition, FlowNode action) {
        CNode node = new CNode(condition);
        node.setAction(action);
        return node;
    }

    private CNode c(String condition) {
        return new CNode(condition);
    }

    private ANode a(String action) {
        return new ANode(action);
    }

    private RNode r(String rule) {
        return new RNode(rule);
    }
}
