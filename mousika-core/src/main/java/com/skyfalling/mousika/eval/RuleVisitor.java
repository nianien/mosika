package com.skyfalling.mousika.eval;

import com.skyfalling.mousika.engine.RuleEngine;
import com.skyfalling.mousika.eval.context.RuleContext;
import com.skyfalling.mousika.eval.listener.ListenerProvider;
import com.skyfalling.mousika.eval.listener.RuleEvent;
import com.skyfalling.mousika.eval.listener.RuleEvent.EventType;
import com.skyfalling.mousika.eval.node.ExprNode;
import com.skyfalling.mousika.eval.node.RuleNode;
import com.skyfalling.mousika.eval.result.EvalResult;
import com.skyfalling.mousika.eval.result.RuleResult;
import com.skyfalling.mousika.exception.RuleEvalException;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * 规则节点执行
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class RuleVisitor extends LinkedHashMap<String, Object> implements RuleContext {

    /**
     * 执行引擎
     */
    private RuleEngine ruleEngine;
    /**
     * 待匹配对象
     */
    private Object data;

    /**
     * 当前规则
     */
    private ThreadLocal<String> currentRule = new ThreadLocal<>();
    /**
     * 缓存评估结果
     */
    private Map<String, EvalResult> evalCache = new ConcurrentSkipListMap<>();

    /**
     * 执行规则根节点
     */
    private EvalNode rootEval = new EvalNode(null);
    /**
     * 当前执行节点
     */
    private ThreadLocal<EvalNode> currentEval = ThreadLocal.withInitial(() -> rootEval);


    /**
     * 指定执行引擎和评估对象
     *
     * @param ruleEngine
     * @param data
     */
    public RuleVisitor(RuleEngine ruleEngine, Object data) {
        this.ruleEngine = ruleEngine;
        this.data = data;
    }


    /**
     * 访问规则节点
     */
    @Override
    public EvalResult visit(RuleNode node) {
        if (node instanceof ExprNode) {
            this.currentRule.set(node.expr());
        }
        EvalNode evalNode = new EvalNode(node);
        boolean isExprNode = node.getClass() == ExprNode.class;
        EvalNode parent = currentEval.get();
        parent.add(evalNode);
        if (!isExprNode) {
            evalNode.setParent(parent);
            currentEval.set(evalNode);
        }
        try {
            EvalResult result = node.eval(this);
            if (!isExprNode) {
                //缓存非叶子节点的执行结果
                this.cache(node.expr(), result);
            }
            return result;
        } finally {
            if (!isExprNode) {
                // 异常路径也必须回溯，避免复用同一 visitor 时把后续节点挂到错误父节点。
                currentEval.set(parent);
            }
        }
    }

    /**
     * 评估叶子规则
     *
     * @param ruleId 规则ID
     */
    @Override
    public EvalResult eval(String ruleId) {
        return evalCache.computeIfAbsent(ruleId, this::doEval);
    }


    @Override
    public String getRule() {
        return currentRule.get();
    }


    @Override
    public List<RuleResult> getRuleResults() {
        List<RuleResult> ruleResults = rootEval.getChildren()
                .stream()
                .map(this::transform)
                .collect(Collectors.toList());
        return ruleResults;
    }


    @Override
    public EvalNode getCurrentEval() {
        return currentEval.get();
    }


    @Override
    public void setCurrentEval(EvalNode node) {
        currentEval.set(node);
    }


    @Override
    public synchronized Object getProperty(Object name) {
        return super.get(name);
    }

    @Override
    public synchronized void setProperty(String name, Object value) {
        super.put(name, value);
    }

    @Override
    public synchronized void removeProperty(String name) {
        super.remove(name);
    }


    /**
     * 执行规则评估
     *
     * @param ruleId 规则ID
     */
    private EvalResult doEval(String ruleId) {
        long begin = System.currentTimeMillis();
        try {
            EvalResult result = new EvalResult(ruleId, ruleEngine.evalRule(ruleId, data, this));
            long end = System.currentTimeMillis();
            ListenerProvider.DEFAULT.onEval(new RuleEvent(EventType.EVAL_SUCCEED, ruleId, result, end - begin));
            return result;
        } catch (Exception e) {
            long end = System.currentTimeMillis();
            ListenerProvider.DEFAULT.onEval(
                    new RuleEvent(EventType.EVAL_FAIL, ruleId, e, end - begin));
            throw new RuleEvalException(ruleId, e.getMessage(), e);
        }
    }

    /**
     * 转换规则执行结果
     *
     * @param node
     * @return
     */
    private RuleResult transform(EvalNode node) {
        RuleNode ruleNode = node.getRuleNode();
        String expr = ruleNode.expr();
        EvalResult result = evalCache.get(expr);
        RuleResult ruleResult = new RuleResult(result, ruleNode instanceof ExprNode ? evalDesc(expr) : "");
        for (EvalNode subNode : node.getChildren()) {
            ruleResult.getSubRules().add(transform(subNode));
        }
        return ruleResult;
    }


    /**
     * 评估规则描述
     *
     * @param ruleId
     * @return
     */
    private String evalDesc(String ruleId) {
        return ruleEngine.evalRuleDesc(ruleId, data, this);
    }


    /**
     * 缓存评估结果
     *
     * @param expr
     * @param result
     */
    private void cache(String expr, EvalResult result) {
        evalCache.put(expr, result);
    }
}


