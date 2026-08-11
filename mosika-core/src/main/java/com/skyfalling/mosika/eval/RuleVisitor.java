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
import com.skyfalling.mosika.exception.RuleNotFoundException;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 单次规则执行使用的节点访问器和规则上下文
 * <p>
 * 附加上下文保存在当前映射中，无参数叶子结果按规则 ID 复用
 * 参数化叶子结果按规则 ID 和规范化参数复用，每个执行节点独立保存实际评估结果
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
     * 当前执行中的叶子评估结果缓存
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
        if (node instanceof ExprNode exprNode) {
            this.currentRule.set(exprNode.getRuleId());
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
            evalNode.setResult(result);
            return result;
        } finally {
            if (!isExprNode) {
                // 异常路径同样回溯到父节点
                currentEval.set(parent);
            }
        }
    }

    /**
     * 使用节点中已经解析和规范化的参数评估叶子规则
     *
     * @param node 叶子节点
     * @return 叶子规则评估结果
     */
    @Override
    public EvalResult eval(ExprNode node) {
        String expression = node.expr();
        return evalCache.computeIfAbsent(expression,
                ignored -> doEval(node.getRuleId(),
                        expression, node.getArguments()));
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
     * 调用规则引擎执行单次叶子规则并发送评估事件
     *
     * @param ruleId     规则 ID
     * @param expression 完整节点表达式
     * @param arguments  当前规则调用参数
     * @return 叶子规则评估结果
     * @throws RuleNotFoundException 规则未注册时抛出
     * @throws RuleEvalException 规则执行失败时抛出
     */
    private EvalResult doEval(String ruleId,
                              String expression,
                              Map<String, Object> arguments) {
        long begin = System.currentTimeMillis();
        try {
            Object value = ruleEngine.evalRule(ruleId, data, this, arguments);
            EvalResult result = new EvalResult(expression, value);
            long end = System.currentTimeMillis();
            ListenerProvider.DEFAULT.onEval(
                    new RuleEvent(EventType.EVAL_SUCCEED, expression, result, end - begin));
            return result;
        } catch (Exception e) {
            long end = System.currentTimeMillis();
            ListenerProvider.DEFAULT.onEval(
                    new RuleEvent(EventType.EVAL_FAIL, expression, e, end - begin));
            if (e instanceof RuleNotFoundException notFound) {
                throw notFound;
            }
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
        EvalResult result = node.getResult();
        String desc = ruleNode instanceof ExprNode exprNode
                ? evalDesc(exprNode.getRuleId(), exprNode.getArguments())
                : "";
        RuleResult ruleResult = new RuleResult(result, desc);
        for (EvalNode subNode : node.getChildren()) {
            ruleResult.getSubRules().add(transform(subNode));
        }
        return ruleResult;
    }


    /**
     * 计算命名规则描述并恢复当前规则 ID
     *
     * @param ruleId    规则 ID
     * @param arguments 当前规则调用参数
     * @return 完成表达式插值的规则描述
     */
    private String evalDesc(String ruleId, Map<String, Object> arguments) {
        String previousRule = currentRule.get();
        currentRule.set(ruleId);
        try {
            return ruleEngine.evalRuleDesc(ruleId, data, this, arguments);
        } finally {
            if (previousRule == null) {
                currentRule.remove();
            } else {
                currentRule.set(previousRule);
            }
        }
    }

}
