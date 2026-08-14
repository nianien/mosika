package com.skyfalling.mosika.suite;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.engine.RuleEngine;
import com.skyfalling.mosika.engine.UdfDefinition;
import com.skyfalling.mosika.eval.RuleVisitor;
import com.skyfalling.mosika.eval.context.RuleContext;
import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.eval.parser.NodeBuilder;
import com.skyfalling.mosika.eval.result.EvalResult;
import com.skyfalling.mosika.eval.result.NodeResult;
import com.skyfalling.mosika.exception.RuleEvalException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 规则定义、UDF 和命名规则节点组成的可执行套件
 * <p>
 * 套件组合 {@link RuleEngine} 和 {@link NodeBuilder}
 * 原子规则脚本由规则引擎预编译，命名复合规则由节点构建器递归编译并缓存
 * 每次执行创建独立的 {@link RuleVisitor}，已注册定义和编译节点可在套件内复用
 * <p>
 * 产品层编排应转换为普通复合 {@link RuleDefinition} 后交给套件执行
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class RuleSuite {

    /**
     * 当前套件的规则注册和 JavaScript 执行引擎
     */
    private final RuleEngine ruleEngine;

    /**
     * 当前套件的 DSL 解析和命名规则节点构建器
     */
    private final NodeBuilder nodeBuilder;


    /**
     * 根据完整规则定义和 UDF 定义创建套件
     * <p>
     * 规则列表应包含当前套件的全部规则，以支持复合规则前向引用和递归编译
     *
     * @param ruleDefinitions 规则定义
     * @param udfDefinitions  UDF 定义
     * @throws IllegalArgumentException 规则 ID 或 UDF 名称重复、规则参数不合法时抛出
     * @throws IllegalStateException    复合规则语法错误或存在循环引用时抛出
     */
    public RuleSuite(List<RuleDefinition> ruleDefinitions, List<UdfDefinition> udfDefinitions) {
        this(RuleEngine.builder()
                        .ruleDefinitions(ruleDefinitions)
                        .udfDefinitions(udfDefinitions)
                        .build(),
                new NodeBuilder(ruleDefinitions));
    }

    /**
     * 使用已有规则引擎创建不包含命名复合规则索引的套件
     * <p>
     * 该入口适用于普通规则 ID、临时 DSL 组合或已经构建的 {@link RuleNode}
     *
     * @param ruleEngine 已完成规则和 UDF 注册的规则引擎
     * @throws NullPointerException 规则引擎为 {@code null} 时抛出
     */
    public RuleSuite(RuleEngine ruleEngine) {
        this(ruleEngine, new NodeBuilder());
    }

    /**
     * 使用指定规则引擎和节点构建器创建套件
     * <p>
     * 规则引擎和节点构建器应使用同一套规则 ID 命名空间
     *
     * @param ruleEngine  已完成规则和 UDF 注册的规则引擎
     * @param nodeBuilder 节点构建器
     * @throws NullPointerException 规则引擎或节点构建器为 {@code null} 时抛出
     */
    public RuleSuite(RuleEngine ruleEngine, NodeBuilder nodeBuilder) {
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine cannot be null");
        this.nodeBuilder = Objects.requireNonNull(nodeBuilder, "nodeBuilder cannot be null");
    }


    /**
     * 解析并执行规则 ID 或任意规则 DSL 表达式
     * <p>
     * 完整 DSL 表达式不缓存，其中引用的命名规则节点会在当前套件内复用
     *
     * @param ruleExpr 规则 ID 或规则 DSL 表达式
     * @param data     用于规则计算的数据对象
     * @return 包含根节点结果和规则详情的执行结果
     * @throws IllegalStateException DSL 语法错误时抛出
     * @throws RuleEvalException     叶子规则未注册或脚本执行失败时抛出
     */
    public NodeResult eval(String ruleExpr, Object data) {
        return eval(nodeBuilder.build(ruleExpr), data);
    }

    /**
     * 使用附加上下文解析并执行规则 ID 或任意规则 DSL 表达式
     * <p>
     * 本次执行直接使用传入的上下文
     *
     * @param ruleExpr 规则 ID 或规则 DSL 表达式
     * @param data     用于规则计算的数据对象
     * @param context  参与本次计算的附加上下文，执行期修改直接作用于该集合
     * @return 包含根节点结果和规则详情的执行结果
     * @throws IllegalStateException DSL 语法错误时抛出
     * @throws RuleEvalException     叶子规则未注册或脚本执行失败时抛出
     */
    public NodeResult eval(String ruleExpr, Object data, Map<String, Object> context) {
        return eval(nodeBuilder.build(ruleExpr), data, context);
    }


    /**
     * 执行已经构建的规则节点
     *
     * @param ruleNode 规则节点
     * @param data     用于规则计算的数据对象
     * @return 包含根节点结果和规则详情的执行结果
     * @throws RuleEvalException 叶子规则未注册或脚本执行失败时抛出
     */
    public NodeResult eval(RuleNode ruleNode, Object data) {
        return doEval(ruleNode, new RuleVisitor(ruleEngine, data));
    }

    /**
     * 使用附加上下文执行已经构建的规则节点
     * <p>
     * 本次执行直接使用传入的上下文
     *
     * @param ruleNode 规则节点
     * @param data     用于规则计算的数据对象
     * @param context  参与本次计算的附加上下文，执行期修改直接作用于该集合
     * @return 包含根节点结果和规则详情的执行结果
     * @throws RuleEvalException 叶子规则未注册或脚本执行失败时抛出
     */
    public NodeResult eval(RuleNode ruleNode, Object data, Map<String, Object> context) {
        return doEval(ruleNode, new RuleVisitor(ruleEngine, data, context));
    }

    /**
     * 执行规则节点并转换为套件结果
     * <p>
     * 节点业务结果本身为 {@link NodeResult} 时直接返回
     *
     * @param ruleNode 规则节点
     * @param context  本次执行的规则上下文
     * @return 规则节点执行结果
     */
    private NodeResult doEval(RuleNode ruleNode, RuleContext context) {
        EvalResult evalResult = context.visit(ruleNode);
        if (evalResult.getResult() instanceof NodeResult nodeResult) {
            return nodeResult;
        }
        return new NodeResult(ruleNode.expr(), evalResult.getResult(), context.getRuleResults());
    }
}
