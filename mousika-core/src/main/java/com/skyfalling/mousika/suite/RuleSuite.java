package com.skyfalling.mousika.suite;

import com.skyfalling.mousika.engine.RuleDefinition;
import com.skyfalling.mousika.engine.RuleEngine;
import com.skyfalling.mousika.engine.UdfDefinition;
import com.skyfalling.mousika.eval.context.RuleContext;
import com.skyfalling.mousika.eval.node.RuleNode;
import com.skyfalling.mousika.eval.parser.NodeBuilder;
import com.skyfalling.mousika.eval.parser.NodeGenerator;
import com.skyfalling.mousika.eval.result.NodeResult;
import com.skyfalling.mousika.exception.NoSceneException;
import lombok.Getter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 规则套件,针对每个规则场景应用所关联规则集合
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
     * 场景列表
     */
    private final Map<String, RuleScene> scenes;


    private final RuleContext ruleContext = null;


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
     * @param sceneDefinitions 场景定义
     */
    public RuleSuite(List<RuleDefinition> ruleDefinitions, List<UdfDefinition> udfDefinitions, List<SceneDefinition> sceneDefinitions) {
        this.ruleEvaluator = create(ruleDefinitions, udfDefinitions);
        this.scenes = sceneDefinitions.stream().map(SceneDefinition::build)
                .collect(Collectors.toMap(RuleScene::getId, Function.identity(), (v1, v2) -> v2));
        current = this;
    }

    /**
     * 获取规则场景
     *
     * @param sceneKey 场景key
     * @return
     */
    public RuleScene getRuleScene(String sceneKey) {
        return scenes.get(sceneKey);
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
     * 校验场景
     *
     * @param sceneId 场景ID
     * @param target  用于规则计算的对象
     * @return
     */
    public NodeResult evalScene(String sceneId, Object target) {
        RuleScene ruleScene = scenes.get(String.valueOf(sceneId));
        if (ruleScene == null) {
            throw new NoSceneException(sceneId, "no scene defined:" + sceneId);
        }
        return this.ruleEvaluator.eval(ruleScene.getRuleNode(), target);
    }


    /**
     * 校验场景
     *
     * @param sceneId 场景ID
     * @param target  用于规则计算的对象
     * @param context 附加上下文信息
     * @return
     */
    public NodeResult evalScene(String sceneId, Object target, Map<String, Object> context) {
        RuleScene ruleScene = scenes.get(String.valueOf(sceneId));
        if (ruleScene == null) {
            throw new NoSceneException(sceneId, "no scene defined:" + sceneId);
        }
        return this.ruleEvaluator.eval(ruleScene.getRuleNode(), target, context);
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
        Iterator<RuleDefinition> it = ruleDefinitions.iterator();
        while (it.hasNext()) {
            RuleDefinition ruleDefinition = it.next();
            switch (ruleDefinition.getUseType()) {
                case 1: //决策表
                    String udf = "udf_rule_table_$" + ruleDefinition.getRuleId();
                    //动态注册UDF
                    udfDefinitions
                            .add(new UdfDefinition(udf, RuleTableUdf.fromJson(ruleDefinition.getExpression())));
                    //修改规则表达式
                    ruleDefinition.setExpression(udf + "($)");
                    break;
                case 2: //复合规则
                    compositeRules.put(ruleDefinition.getRuleId(), ruleDefinition.getExpression());
                    break;
                default:
            }
        }
        ruleEngine.ruleDefinitions(ruleDefinitions);
        ruleEngine.udfDefinitions(udfDefinitions);
        NodeBuilder.setGenerator(NodeGenerator.create(compositeRules));
        return new RuleEvaluator(ruleEngine.build());
    }
}
