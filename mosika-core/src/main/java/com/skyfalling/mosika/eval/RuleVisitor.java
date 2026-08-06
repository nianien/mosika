package com.skyfalling.mosika.eval;

import com.skyfalling.mosika.engine.RuleEngine;
import com.skyfalling.mosika.eval.context.RuleContext;
import com.skyfalling.mosika.eval.listener.ListenerProvider;
import com.skyfalling.mosika.eval.listener.RuleEvent;
import com.skyfalling.mosika.eval.listener.RuleEvent.EventType;
import com.skyfalling.mosika.eval.node.ExprNode;
import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.eval.result.EvalResult;
import com.skyfalling.mosika.eval.result.RuleResult;
import com.skyfalling.mosika.exception.RuleEvalException;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 单次规则执行使用的节点访问器和规则上下文
 * <p>
 * 附加上下文保存在当前映射中，节点评估结果和执行树用于叶子复用及详情组装
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class RuleVisitor extends LinkedHashMap<String, Object> implements RuleContext {

    /**
     * 当前执行使用的规则引擎
     */
    private RuleEngine ruleEngine;
    /**
     * 当前执行的目标对象
     */
    private Object data;

    /**
     * 当前线程正在评估的命名规则 ID
     */
    private ThreadLocal<String> currentRule = new ThreadLocal<>();
    /**
     * 当前执行中按表达式保存的节点评估结果
     */
    private Map<String, EvalResult> evalCache = new ConcurrentHashMap<>();

    /**
     * 当前执行树的虚拟根节点
     */
    private EvalNode rootEval = new EvalNode(null);
    /**
     * 当前线程正在访问的执行节点
     */
    private ThreadLocal<EvalNode> currentEval = ThreadLocal.withInitial(() -> rootEval);


    /**
     * 创建单次规则执行上下文
     *
     * @param ruleEngine 规则引擎
     * @param data       目标对象
     */
    public RuleVisitor(RuleEngine ruleEngine, Object data) {
        this.ruleEngine = ruleEngine;
        this.data = data;
    }


    /**
     * 访问规则节点并记录执行层级和评估结果
     *
     * @param node 规则节点
     * @return 节点评估结果
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
                // 缓存非叶子节点结果以组装执行详情
                this.cache(node.expr(), result);
            }
            return result;
        } finally {
            if (!isExprNode) {
                // 异常路径同样回溯到父节点
                currentEval.set(parent);
            }
        }
    }

    /**
     * 按规则 ID 评估叶子规则并复用当前执行中的已有结果
     *
     * @param ruleId 规则 ID
     * @return 叶子规则评估结果
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
     * 调用规则引擎执行叶子规则并发送评估事件
     *
     * @param ruleId 规则 ID
     * @return 叶子规则评估结果
     * @throws RuleEvalException 规则未注册或执行失败时抛出
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
     * 把执行树节点转换为递归规则详情
     *
     * @param node 执行树节点
     * @return 规则详情
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
     * 计算命名规则描述并恢复当前规则 ID
     *
     * @param ruleId 规则 ID
     * @return 完成表达式插值的规则描述
     */
    private String evalDesc(String ruleId) {
        String previousRule = currentRule.get();
        currentRule.set(ruleId);
        try {
            return ruleEngine.evalRuleDesc(ruleId, data, this);
        } finally {
            if (previousRule == null) {
                currentRule.remove();
            } else {
                currentRule.set(previousRule);
            }
        }
    }


    /**
     * 按节点表达式保存评估结果
     *
     * @param expr 节点表达式
     * @param result 节点评估结果
     */
    private void cache(String expr, EvalResult result) {
        evalCache.put(expr, result);
    }
}
