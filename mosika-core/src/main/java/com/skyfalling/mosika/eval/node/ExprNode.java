package com.skyfalling.mosika.eval.node;

import com.skyfalling.mosika.eval.context.RuleContext;
import com.skyfalling.mosika.eval.result.EvalResult;
import com.skyfalling.mosika.utils.JsonUtils;
import lombok.Getter;

import java.util.Map;

/**
 * 由规则上下文按规则 ID 求值的普通叶子节点
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class ExprNode implements RuleNode {

    /**
     * 规则 ID
     */
    @Getter
    private final String ruleId;

    /**
     * 当前规则调用绑定的参数对象
     */
    @Getter
    private final Map<String, Object> arguments;

    /**
     * 包含调用参数的完整表达式，无参数时为 {@code null}
     */
    private final String expression;

    /**
     * 创建普通叶子节点
     *
     * @param ruleId 规则 ID
     */
    public ExprNode(String ruleId) {
        this(ruleId, null);
    }

    /**
     * 创建带调用参数的普通叶子节点
     *
     * @param ruleId 规则 ID
     * @param arguments  当前规则调用绑定的 JSON 参数
     */
    public ExprNode(String ruleId, String arguments) {
        this.ruleId = ruleId;
        this.arguments = arguments == null ? null : parseArguments(arguments);
        this.expression = this.arguments == null
                ? null
                : ruleId + "(\"\"\"" + JsonUtils.toJson(this.arguments) + "\"\"\")";
    }

    /**
     * 使用指定调用参数复制当前命名规则节点
     *
     * @param arguments 当前规则调用绑定的 JSON 参数
     * @return 带调用参数的命名规则节点
     */
    public ExprNode withArguments(String arguments) {
        return new ExprNode(ruleId, arguments);
    }

    @Override
    public EvalResult eval(RuleContext context) {
        return context.eval(this);
    }

    @Override
    public String expr() {
        return expression == null ? ruleId : expression;
    }

    /**
     * 解析当前规则调用绑定的 JSON 参数
     *
     * @param arguments JSON 参数
     * @return 参数对象
     * @throws IllegalArgumentException 参数不是合法 JSON 对象时抛出
     */
    private static Map<String, Object> parseArguments(String arguments) {
        Map<String, Object> values;
        try {
            values = JsonUtils.toMap(arguments, String.class, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid rule arguments", e);
        }
        if (values == null) {
            throw new IllegalArgumentException("rule arguments must be a JSON object");
        }
        return values;
    }

    @Override
    public String toString() {
        return expr();
    }
}
