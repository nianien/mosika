package com.skyfalling.mousika.suite;

import com.skyfalling.mousika.engine.RuleDefinition;
import com.skyfalling.mousika.engine.RuleEngine;
import com.skyfalling.mousika.engine.UdfDefinition;
import com.skyfalling.mousika.eval.node.RuleNode;
import com.skyfalling.mousika.eval.parser.NodeGenerator;
import com.skyfalling.mousika.eval.result.NodeResult;
import com.skyfalling.mousika.exception.NoRuleFlowException;
import lombok.Getter;

import java.util.*;

/**
 * 规则套件，负责装配规则、UDF和规则流
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public class RuleSuite {
    /**
     * 规则判定器
     */
    private final RuleEvaluator ruleEvaluator;
    /**
     * 规则流列表
     */
    private final Map<String, RuleFlow> flows;


    private static volatile RuleSuite current;

    /**
     * 获取当前规则套件
     *
     * @return
     */
    public static RuleSuite get() {
        return current;
    }

    /**
     * 构建规则套件
     *
     * @param ruleDefinitions  规则定义
     * @param udfDefinitions   udf定义
     * @param flowDefinitions  规则流定义
     */
    public RuleSuite(List<RuleDefinition> ruleDefinitions, List<UdfDefinition> udfDefinitions,
                     List<RuleFlowDefinition> flowDefinitions) {
        this(ruleDefinitions, udfDefinitions, flowDefinitions, true);
    }

    private RuleSuite(List<RuleDefinition> ruleDefinitions, List<UdfDefinition> udfDefinitions,
                      List<RuleFlowDefinition> flowDefinitions, boolean publish) {
        this.ruleEvaluator = create(ruleDefinitions, udfDefinitions);
        Map<String, RuleFlow> compiledFlows = new LinkedHashMap<>();
        for (RuleFlowDefinition definition : flowDefinitions) {
            RuleFlow flow = new RuleFlow(definition.getId(), ruleEvaluator.compile(definition.getDsl()));
            if (compiledFlows.putIfAbsent(flow.getId(), flow) != null) {
                throw new IllegalArgumentException("duplicate rule flow id: " + flow.getId());
            }
        }
        this.flows = Collections.unmodifiableMap(compiledFlows);
        if (publish) {
            current = this;
        }
    }

    /**
     * 只验证一组定义能否完整构造，不替换进程当前正在使用的规则套件。
     *
     * @param ruleDefinitions 规则定义
     * @param udfDefinitions  UDF定义
     * @param flowDefinitions 规则流定义
     */
    public static void validate(List<RuleDefinition> ruleDefinitions, List<UdfDefinition> udfDefinitions,
                                List<RuleFlowDefinition> flowDefinitions) {
        prepare(ruleDefinitions, udfDefinitions, flowDefinitions);
    }

    /**
     * 构造候选套件但不替换当前运行快照，供持久化事务提交前完成全量预编译。
     */
    public static RuleSuite prepare(List<RuleDefinition> ruleDefinitions, List<UdfDefinition> udfDefinitions,
                                    List<RuleFlowDefinition> flowDefinitions) {
        return new RuleSuite(ruleDefinitions, udfDefinitions, flowDefinitions, false);
    }

    /**
     * 原子发布一份已经完整构造的候选套件。
     */
    public static void publish(RuleSuite candidate) {
        current = Objects.requireNonNull(candidate, "candidate RuleSuite cannot be null");
    }

    /**
     * 获取规则流
     *
     * @param flowId 规则流ID
     * @return 规则流
     */
    public RuleFlow getRuleFlow(String flowId) {
        return flows.get(flowId);
    }

    /**
     * 校验规则集合
     *
     * @param ruleNode 规则结合
     * @param target   用于规则计算的对象
     * @return
     */
    public NodeResult evalRule(RuleNode ruleNode, Object target) {
        return ruleEvaluator.eval(ruleNode, target);
    }


    /**
     * 执行规则流
     *
     * @param flowId 规则流ID
     * @param target 用于规则计算的对象
     * @return 规则流执行结果
     */
    public NodeResult evalFlow(String flowId, Object target) {
        RuleFlow ruleFlow = flows.get(flowId);
        if (ruleFlow == null) {
            throw new NoRuleFlowException(flowId, "no rule flow defined:" + flowId);
        }
        return this.ruleEvaluator.eval(ruleFlow.getRoot(), target);
    }


    /**
     * 执行规则流
     *
     * @param flowId  规则流ID
     * @param target  用于规则计算的对象
     * @param context 附加上下文信息
     * @return 规则流执行结果
     */
    public NodeResult evalFlow(String flowId, Object target, Map<String, Object> context) {
        RuleFlow ruleFlow = flows.get(flowId);
        if (ruleFlow == null) {
            throw new NoRuleFlowException(flowId, "no rule flow defined:" + flowId);
        }
        return this.ruleEvaluator.eval(ruleFlow.getRoot(), target, context);
    }

    /**
     * 评估表达式
     *
     * @param expr   规则表达式
     * @param target 用于规则计算的对象
     * @return
     */
    public NodeResult evalExpr(String expr, Object target) {
        return this.ruleEvaluator.eval(expr, target);
    }


    /**
     * 创建规则评估器
     *
     * @param ruleDefinitions 规则定义
     * @param udfDefinitions  udf定义
     * @return
     */
    private RuleEvaluator create(List<RuleDefinition> ruleDefinitions, List<UdfDefinition> udfDefinitions) {
        Map<String, String> compositeRules = new HashMap<>();
        RuleEngine.RuleEngineBuilder ruleEngine = RuleEngine.builder();
        List<RuleDefinition> executableRules = new ArrayList<>();
        List<UdfDefinition> executableUdfs = new ArrayList<>(udfDefinitions);
        for (RuleDefinition original : ruleDefinitions) {
            RuleDefinition ruleDefinition = new RuleDefinition(
                    original.getRuleId(), original.getExpression(), original.getDesc(), original.getUseType());
            switch (ruleDefinition.getUseType()) {
                case 1: //决策表
                    String udf = "udf_rule_table_$" + ruleDefinition.getRuleId();
                    //动态注册UDF
                    executableUdfs
                            .add(new UdfDefinition(udf, RuleTableUdf.fromJson(ruleDefinition.getExpression())));
                    //修改规则表达式
                    ruleDefinition.setExpression(udf + "($)");
                    break;
                case 2: //复合规则
                    compositeRules.put(ruleDefinition.getRuleId(), ruleDefinition.getExpression());
                    break;
                default:
            }
            executableRules.add(ruleDefinition);
        }
        ruleEngine.ruleDefinitions(executableRules);
        ruleEngine.udfDefinitions(executableUdfs);
        return new RuleEvaluator(ruleEngine.build(), NodeGenerator.create(compositeRules));
    }
}
