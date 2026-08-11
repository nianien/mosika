package com.skyfalling.mosika.engine;

import com.skyfalling.mosika.eval.result.NaResult;
import com.skyfalling.mosika.exception.RuleNotFoundException;
import com.skyfalling.mosika.utils.Constants;
import com.skyfalling.mosika.utils.JsRuntime;
import lombok.Builder;
import lombok.Singular;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * 规则定义和 UDF 的注册执行引擎
 * <p>
 * 构造阶段注册内置规则和输入定义，预编译原子规则脚本、规则描述模板和 UDF
 * 执行阶段为每次调用创建独立的 JavaScript 上下文并绑定目标对象、规则上下文、节点参数和 UDF
 * 复合规则 DSL 由 {@link com.skyfalling.mosika.eval.parser.NodeBuilder NodeBuilder} 编译
 * 本类只负责其中普通规则 ID 的脚本求值
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class RuleEngine {

    /**
     * 按规则 ID 保存内置规则和输入规则定义
     */
    private Map<String, RuleDefinition> ruleDefinitions = new HashMap<>();
    /**
     * 按 JavaScript 源码缓存已验证的规则脚本
     */
    private final ConcurrentMap<String, Source> compiledScripts = new ConcurrentHashMap<>();
    /**
     * 按描述模板缓存已验证的 JavaScript 脚本
     */
    private final ConcurrentMap<String, Source> compiledDesc = new ConcurrentHashMap<>();
    /**
     * 按顶层名称保存编译后的 UDF 绑定对象
     */
    private Map<String, Object> compiledUdfs;

    /**
     * 注册规则定义和 UDF 定义并完成可执行内容的预编译
     *
     * @param ruleDefinitions 规则定义
     * @param udfDefinitions  UDF 定义
     * @throws IllegalArgumentException 规则 ID 或 UDF 名称冲突、UDF 定义不合法时抛出
     */
    @Builder
    public RuleEngine(@Singular List<RuleDefinition> ruleDefinitions, @Singular List<UdfDefinition> udfDefinitions) {
        // 注册内置规则
        this.register(new RuleDefinition(Constants.TRUE, Constants.TRUE, "SUCCESS"));
        this.register(new RuleDefinition(Constants.FALSE, Constants.FALSE, "FAILED"));
        this.register(new RuleDefinition(Constants.NULL, "Java.type('" + NaResult.class.getName() + "').DEFAULT", "NULL"));
        this.register(new RuleDefinition(Constants.NOP, "Java.type('" + NaResult.class.getName() + "').DEFAULT", "NOP"));
        ruleDefinitions.forEach(this::register);
        this.compiledUdfs = new UdfContainer(udfDefinitions).compile();
    }

    /**
     * 按规则 ID 执行已注册定义中的 JavaScript 表达式
     * <p>
     * 命名复合规则应先由 {@link com.skyfalling.mosika.eval.parser.NodeBuilder NodeBuilder}
     * 展开为规则节点
     *
     * @param ruleId  规则 ID
     * @param root    规则计算的目标对象，通过 {@code $} 访问
     * @param context 规则执行上下文，通过 {@code $$} 访问
     * @return JavaScript 表达式返回值
     * @throws IllegalArgumentException 规则未注册时抛出
     */
    public Object evalRule(String ruleId, Object root, Object context) {
        return evalRule(ruleId, root, context, null);
    }

    /**
     * 使用当前节点参数执行已注册定义中的 JavaScript 表达式
     *
     * @param ruleId   规则 ID
     * @param root     规则计算的目标对象，通过 {@code $} 访问
     * @param context  规则执行上下文，通过 {@code $$} 访问
     * @param arguments 当前规则调用绑定的参数对象，通过 {@code $args} 访问
     * @return JavaScript 表达式返回值
     * @throws RuleNotFoundException 规则未注册时抛出
     */
    public Object evalRule(String ruleId, Object root, Object context, Map<String, Object> arguments) {
        RuleDefinition ruleDefinition = this.ruleDefinitions.get(ruleId);
        if (ruleDefinition == null) {
            throw new RuleNotFoundException(ruleId);
        }
        return doEval(compile(ruleDefinition.getExpression()), root, context, arguments);
    }

    /**
     * 计算指定规则的描述模板
     *
     * @param ruleId  规则 ID
     * @param root    规则计算的目标对象，通过 {@code $} 访问
     * @param context 规则执行上下文，通过 {@code $$} 访问
     * @return 完成表达式插值的规则描述
     * @throws IllegalArgumentException 规则未注册时抛出
     */
    public String evalRuleDesc(String ruleId, Object root, Object context) {
        return evalRuleDesc(ruleId, root, context, null);
    }

    /**
     * 使用当前节点参数计算指定规则的描述模板
     *
     * @param ruleId   规则 ID
     * @param root     规则计算的目标对象，通过 {@code $} 访问
     * @param context  规则执行上下文，通过 {@code $$} 访问
     * @param arguments 当前规则调用绑定的参数对象，通过 {@code $args} 访问
     * @return 完成表达式插值的规则描述
     * @throws RuleNotFoundException 规则未注册时抛出
     */
    public String evalRuleDesc(String ruleId, Object root, Object context, Map<String, Object> arguments) {
        RuleDefinition ruleDefinition = this.ruleDefinitions.get(ruleId);
        if (ruleDefinition == null) {
            throw new RuleNotFoundException(ruleId);
        }
        return (String) doEval(compileDesc(ruleDefinition.getDesc()), root, context, arguments);
    }

    /**
     * 直接执行 JavaScript 表达式
     *
     * @param expression JavaScript 表达式
     * @param root       规则计算的目标对象，通过 {@code $} 访问
     * @param context    规则执行上下文，通过 {@code $$} 访问
     * @return JavaScript 表达式返回值
     */
    public Object evalExpr(String expression, Object root, Object context) {
        return doEval(compile(expression), root, context, null);
    }


    /**
     * 在独立 JavaScript 上下文中执行已编译脚本
     *
     * @param script      已编译脚本
     * @param root        规则计算的目标对象
     * @param ruleContext 规则执行上下文
     * @param arguments   当前规则调用绑定的参数对象
     * @return 转换为 Java 对象的脚本返回值
     */
    private Object doEval(Source script, Object root, Object ruleContext, Map<String, Object> arguments) {
        try (Context context = JsRuntime.createContext()) {
            Value bindings = context.getBindings(JsRuntime.LANGUAGE_ID);
            bindings.putMember("$", root);
            bindings.putMember("$$", ruleContext);
            bindings.putMember("$args", arguments == null ? Map.of() : arguments);
            compiledUdfs.forEach(bindings::putMember);
            return JsRuntime.toJava(context.eval(script));
        }
    }

    /**
     * 注册规则并预编译需要直接执行的内容
     *
     * @param definition 规则定义
     * @throws IllegalArgumentException 规则 ID 已存在时抛出
     */
    private void register(RuleDefinition definition) {
        if (ruleDefinitions.containsKey(definition.getRuleId())) {
            throw new IllegalArgumentException("duplicate function defined: " + definition.getRuleId());
        }
        ruleDefinitions.put(definition.getRuleId(), definition);
        if (definition.getRuleType() == RuleDefinition.RULE_TYPE_ATOMIC) {
            // 仅原子规则表达式作为 JavaScript 脚本预编译
            compile(definition.getExpression());
        }
        compileDesc(definition.getDesc());
    }

    /**
     * 获取或编译 JavaScript 规则脚本
     *
     * @param expression JavaScript 表达式
     * @return 已验证的脚本
     */
    private Source compile(String expression) {
        return compiledScripts.computeIfAbsent(expression, this::compileExpression);
    }

    /**
     * 编译 JavaScript 规则表达式
     * <p>
     * 顶层对象字面量会按代码块解析，首尾为大括号时补充分组括号
     * 其他表达式保持原样
     *
     * @param expression JavaScript 表达式
     * @return 已验证的脚本
     */
    private Source compileExpression(String expression) {
        String trimmed = expression.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return doCompile("(" + trimmed + ")");
        }
        return doCompile(expression);
    }


    /**
     * 获取或编译规则描述模板
     * <p>
     * 描述通过 {@code String.raw} 模板支持访问 {@code $.agent}、{@code $$.agent} 和 {@code $args.agent}
     *
     * @param originDesc 规则描述模板
     * @return 已验证的描述模板脚本
     */
    private Source compileDesc(String originDesc) {
        return compiledDesc.computeIfAbsent(originDesc,
                desc -> doCompile("String.raw`" + desc + "`"));
    }


    /**
     * 创建并验证 JavaScript 脚本
     *
     * @param expression JavaScript 源码
     * @return 已通过语法解析的脚本
     */
    private Source doCompile(String expression) {
        Source source = JsRuntime.createSource(expression, "rule-" + Integer.toHexString(expression.hashCode()));
        try (Context context = JsRuntime.createContext()) {
            context.parse(source);
        }
        return source;
    }


}
