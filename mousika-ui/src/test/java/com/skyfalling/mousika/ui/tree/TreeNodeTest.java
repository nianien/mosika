package com.skyfalling.mousika.ui.tree;

import com.skyfalling.mousika.eval.node.RuleNode;
import com.skyfalling.mousika.eval.parser.NodeBuilder;
import com.skyfalling.mousika.ui.tree.node.TreeNode;
import com.skyfalling.mousika.ui.tree.node.flow.*;
import com.skyfalling.mousika.ui.tree.node.rule.HNode;
import com.skyfalling.mousika.ui.tree.node.rule.LNode;
import com.skyfalling.mousika.ui.tree.node.rule.RNode;
import com.skyfalling.mousika.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created on 2023/4/24
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class TreeNodeTest {

    @Test
    public void testHitsRoundTrip() {
        String expression = "hits(_,2,101,102,103)?104:105";
        TreeNode tree = new TreeNode().fromRule(NodeBuilder.build(expression));
        String json = JsonUtils.toJson(tree);
        assertTrue(json.contains("\"type\":\"H\""));
        assertFalse(json.contains("\"minHits\""));
        assertTrue(json.contains("\"maxHits\":2"));

        TreeNode restored = JsonUtils.toBean(json, TreeNode.class);
        assertEquals(expression, restored.toRule().expr());
        assertEquals(json, JsonUtils.toJson(restored));
    }

    @Test
    public void testTopLevelHits() {
        String expression = "hits(2,_,101,102,103)";
        TreeNode tree = new TreeNode().fromRule(NodeBuilder.build(expression));
        JNode judge = assertInstanceOf(JNode.class, tree.getNext());
        assertInstanceOf(HNode.class, judge.getRule());
        assertEquals(expression, judge.ruleExpr());
        assertEquals(expression, tree.toRule().expr());

        TreeNode restored = JsonUtils.toBean(JsonUtils.toJson(tree), TreeNode.class);
        JNode restoredJudge = assertInstanceOf(JNode.class, restored.getNext());
        assertInstanceOf(HNode.class, restoredJudge.getRule());
        assertEquals(expression, restoredJudge.ruleExpr());
        assertEquals(expression, restored.toRule().expr());
    }

    @Test
    public void testTopLevelLogicRoundTrip() {
        for (String expression : new String[]{
                "c1&&!c2",
                "!c1",
                "!(c1&&c2)",
                "!hits(1,2,c1,c2,c3)"
        }) {
            TreeNode tree = new TreeNode().fromRule(NodeBuilder.build(expression));
            assertEquals(expression, tree.toRule().expr());

            TreeNode restored = TreeNode.fromJson(tree.toJson());
            assertEquals(expression, restored.toRule().expr());
        }
    }

    @Test
    public void testSerialWithNonActionNodes() {
        String expression = "a0->(c1?a1)->a2";
        TreeNode tree = new TreeNode().fromRule(NodeBuilder.build(expression));
        assertInstanceOf(SNode.class, tree.getNext());

        RuleNode restoredRule = tree.toRule();
        assertEquals("∅->a0->(c1?a1)->a2", restoredRule.expr());
        assertEquals(tree.toJson(), new TreeNode().fromRule(NodeBuilder.build(restoredRule.expr())).toJson());
    }

    @Test
    public void testCollectAndValidate() {
        TreeNode tree = new TreeNode().fromRule(NodeBuilder.build(
                "a0->(c1?hits(1,2,r1,r_2)?a1:a2)->a3"));
        tree.validate();
        assertEquals(Set.of("a0", "c1", "r1", "r_2", "a1", "a2", "a3"), tree.collect());

        HNode invalidHits = new HNode(3, 3);
        invalidHits.addRule(r("r1")).addRule(r("r2"));
        JNode judge = new JNode();
        judge.setRule(invalidHits);
        TreeNode invalidTree = new TreeNode();
        invalidTree.setNext(judge);
        assertThrows(IllegalStateException.class, invalidTree::validate);

        DNode redundantDecision = new DNode();
        redundantDecision.addBranch(c("c1", "a1"));
        TreeNode redundantTree = new TreeNode();
        redundantTree.setNext(redundantDecision);
        assertThrows(IllegalStateException.class, redundantTree::validate);
        assertThrows(IllegalArgumentException.class, redundantTree::toRule);
    }

    @Test
    public void testFlow() {
        TreeNode tree = new TreeNode();
        ANode a1 = a("a1");
        ANode a2 = a("a2");
        CNode c3 = c("c3");
        ANode a3 = a("a3");
        ANode a4 = a("a4");
        tree.setNext(a1);
        a1.setNext(a2);
        a2.setNext(c3);
        c3.setAction(a3);
        a3.setNext(a4);
        RuleNode rule = tree.toRule();
        System.out.println(rule);
        TreeNode t2 = new TreeNode().fromRule(rule);
        System.out.println(JsonUtils.toJson(t2));
        assertEquals("{\"type\":\"T\",\"expr\":\"\",\"next\":{\"type\":\"A\",\"expr\":\"a1\",\"next\":{\"type\":\"A\",\"expr\":\"a2\",\"next\":{\"type\":\"C\",\"expr\":\"c3\",\"negative\":false,\"action\":{\"type\":\"A\",\"expr\":\"a3\",\"next\":{\"type\":\"A\",\"expr\":\"a4\"}}}}}}",
                JsonUtils.toJson(t2));
    }


    @Test
    public void testDecision() {
        TreeNode tree = new TreeNode();
        ANode a0 = new ANode("a0");
        tree.setNext(a0);
        DNode d1 = new DNode();
        a0.setNext(d1);
        d1.addBranch(c("c1", "a1"));
        d1.addBranch(c("c2", "a2"));
        d1.setAction(new ANode("a3"));
        RuleNode rule = tree.toRule();
        RuleNode ruleNode3 = NodeBuilder.build(rule.expr());
        System.out.println(JsonUtils.toJson(rule));
        System.out.println(JsonUtils.toJson(ruleNode3));
        TreeNode t2 = new TreeNode().fromRule(rule);

        RuleNode rule2 = t2.toRule();
        System.out.println(rule);
        System.out.println(rule2);
        System.out.println(JsonUtils.toJson(tree));
        System.out.println(JsonUtils.toJson(t2));
        assertEquals(rule.expr(), rule2.expr());
        assertEquals(JsonUtils.toJson(tree), JsonUtils.toJson(t2));
    }


    @Test
    public void testSerial() {
        TreeNode tree = new TreeNode();
        SNode s1 = new SNode();
        tree.setNext(s1);
        CNode c1 = new CNode("c1");
        c1.setAction(new ANode("a1"));
        s1.addBranch(c1);
        CNode c2 = new CNode("c2");
        c2.setAction(new ANode("a2"));
        s1.addBranch(c2);
        CNode c3 = new CNode("c3");
        c3.setAction(new ANode("a3"));
        s1.addBranch(c3);

        TreeNode t2 = JsonUtils.toBean(JsonUtils.toJson(tree), TreeNode.class);
        RuleNode rule = tree.toRule();
        RuleNode rule2 = t2.toRule();
        TreeNode t3 = new TreeNode().fromRule(rule2);
        System.out.println(rule2);
        System.out.println(JsonUtils.toJson(tree));
        System.out.println(JsonUtils.toJson(t2));
        System.out.println(JsonUtils.toJson(t3));
        assertEquals(rule.expr(), rule2.expr());
        assertEquals(JsonUtils.toJson(tree), JsonUtils.toJson(t3));
    }

    @Test
    public void testSerial2() {
        TreeNode tree = new TreeNode();
        SNode s1 = new SNode();
        tree.setNext(s1);
        ANode a1 = new ANode("a1");
        ANode a2 = new ANode("a2");
        a1.setNext(a2);
        s1.addBranch(a1);
        ANode a3 = new ANode("a3");
        ANode a4 = new ANode("a4");
        a3.setNext(a4);
        s1.addBranch(a3);
        RuleNode rule = tree.toRule();
        System.out.println(rule);
        assertEquals("∅->(a1->a2)->(a3->a4)", rule.expr());
        TreeNode t2 = new TreeNode().fromRule(rule);
        System.out.println(JsonUtils.toJson(t2));
        assertEquals(JsonUtils.toJson(tree), JsonUtils.toJson(t2));
    }

    @Test
    public void testParallel() {
        TreeNode tree = new TreeNode();
        PNode p1 = new PNode();
        ANode a0 = a("a0");
        tree.setNext(a0);
        a0.setNext(p1);
        p1.addBranch(a("a1"));
        p1.addBranch(a("a2"));
        p1.addBranch(a("a3"));
        RuleNode rule = tree.toRule();
        RuleNode reparsed = NodeBuilder.build(rule.expr());
        TreeNode restored = new TreeNode().fromRule(reparsed);
        assertEquals(rule.expr(), reparsed.expr());
        assertEquals(JsonUtils.toJson(tree), JsonUtils.toJson(restored));
    }

    @Test
    public void testJudge() {
        TreeNode tree = new TreeNode();
        ANode a0 = new ANode("a0");
        tree.setNext(a0);
        DNode d1 = new DNode();
        a0.setNext(d1);
        JNode j1 = new JNode();
        j1.setRule(LNode.and()
                .addRule(LNode.or().addRule(r("r1")).addRule(r("r2")))
                .addRule(LNode.or().addRule(r("r3")).addRule(r("r4"))));
        j1.setAction(new ANode("a1"));
        d1.addBranch(j1);

        d1.addBranch(c("c2","a2"));
        d1.setAction(a("a3"));
        RuleNode rule = JsonUtils.toBean(JsonUtils.toJson(tree), TreeNode.class).toRule();
        TreeNode t2 = new TreeNode().fromRule(rule);
        System.out.println(JsonUtils.toJson(t2));
        System.out.println(rule);
        System.out.println(t2.toRule());
        assertEquals(JsonUtils.toJson(tree), JsonUtils.toJson(t2));
        assertEquals(rule.expr(), t2.toRule().expr());
    }

    @Test
    public void testFromCaseNode() {
        RuleNode caseNode = NodeBuilder.build("c1?a1:c2&&!c3?a2:a3");
        System.out.println(JsonUtils.toJson(new TreeNode().fromRule(caseNode)));
    }

    @Test
    public void testFromLogicNode() {
        RuleNode caseNode = NodeBuilder.build("c1&&!c2");
        System.out.println(JsonUtils.toJson(new TreeNode().fromRule(caseNode)));
    }

    @Test
    public void testFromSerNode() {
        RuleNode caseNode = NodeBuilder.build("c1->c2->c3");
        System.out.println(JsonUtils.toJson(new TreeNode().fromRule(caseNode)));
    }


    @Test
    public void testFromExprNode() {
        RuleNode rule = NodeBuilder.build("c1");
        TreeNode t1 = new TreeNode().fromRule(rule);
        System.out.println(JsonUtils.toJson(t1));
        RuleNode rule2 = NodeBuilder.build("c1?nop");
        TreeNode t2 = new TreeNode().fromRule(rule2);
        System.out.println(JsonUtils.toJson(t2));
    }


    @Test
    public void testTree() {
        TreeNode tree = new TreeNode();
        SNode s0 = new SNode();
        tree.setNext(s0);
        PNode pn = new PNode();
        s0.addBranch(pn);
        //build pn
        pn.addBranch(a("a1"));
        pn.addBranch(a("a2"));
        pn.addBranch(a("a3"));


        //build d1
        DNode d1 = new DNode();
        s0.addBranch(d1);

        // add branch d1_j
        JNode d1_j = new JNode();
        //add d1_j
        d1_j.setRule(LNode.or().addRule(r("c1")).addRule(LNode.and().addRule(r("c2")).addRule(r("c3"))));

        //add d1_j_s
        SNode d1_j_s = new SNode();


        DNode d1_j_s_d = new DNode();
        d1_j_s_d.addBranch(c("c1", "a1"));
        JNode d1_j_s_d_j = new JNode();
        d1_j_s_d_j.setRule(LNode.and().addRule(r("c2")).addRule(r("c3")));
        d1_j_s_d_j.setAction(a("a3"));
        d1_j_s_d.addBranch(d1_j_s_d_j);

        d1_j_s.addBranch(d1_j_s_d);
        d1_j_s.addBranch(c("c4", "a4"));

        d1_j.setAction(d1_j_s);

        d1.addBranch(d1_j);

        //add d1_c5 for d1
        CNode d1_c5 = new CNode("c5");
        DNode c5_d = new DNode();
        c5_d.addBranch(c("c6", "a6"));
        c5_d.addBranch(c("c7", "a7"));
        c5_d.setAction(a("a5"));
        d1_c5.setAction(c5_d);
        d1.addBranch(d1_c5);

        SNode d1_s = new SNode();
        d1_s.addBranch(c("c8", "a8"));
        d1_s.addBranch(c("c9", "a9"));
        d1.setAction(d1_s);
        System.out.println(JsonUtils.toJson(tree));
        System.out.println(tree.toRule());
        TreeNode t2 = new TreeNode().fromRule(tree.toRule());
        assertEquals(JsonUtils.toJson(tree), JsonUtils.toJson(t2));
    }


    private CNode c(String c, String a) {
        CNode cn = new CNode(c);
        cn.setAction(new ANode(a));
        return cn;
    }

    private CNode c(String c) {
        return new CNode(c);
    }

    private ANode a(String a) {
        return new ANode(a);
    }

    private RNode r(String r) {
        return new RNode(r);
    }
}
