package com.skyfalling.mosika.engine;

import com.skyfalling.mosika.eval.result.NaResult;
import com.skyfalling.mosika.exception.RuleNotFoundException;
import com.skyfalling.mosika.utils.Constants;
import com.skyfalling.mosika.utils.JsRuntime;
import lombok.Builder;
import lombok.Singular;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptableObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;


/**
 * 规则定义和 UDF 的注册执行引擎
 * <p>
 * 构造阶段注册内置规则和输入定义，预编译原子规则脚本、规则描述模板和 UDF
 * 执行阶段为每次调用创建独立的 JavaScript 作用域
 * 复合规则 DSL 由 {@link com.skyfalling.mosika.eval.parser.NodeBuilder NodeBuilder} 编译
 * 本类只负责其中普通规则 ID 的脚本求值
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class RuleEngine {

    private static final AtomicLong SOURCE_SEQUENCE = new AtomicLong();

    /**
     * 按规则 ID 保存内置规则和输入规则定义
     */
    private Map<String, RuleDefinition> ruleDefinitions = new HashMap<>();

    private final Map<String, Script> compiledRules = new HashMap<>();

    /**
     * 按 JavaScript 源码缓存已编译的规则脚本
     */
    private final ConcurrentMap<String, Script> compiledScripts = new ConcurrentHashMap<>();

    /**
     * 按描述模板缓存已编译的 JavaScript 脚本
     */
    private final ConcurrentMap<String, Script> compiledDesc = new ConcurrentHashMap<>();

    private final UdfContainer udfContainer;

    /**
     * 注册规则定义和 UDF 定义并完成可执行内容的预编译
     *
     * @param ruleDefinitions 规则定义
     * @param udfDefinitions  UDF 定义
     * @throws IllegalArgumentException 规则 ID 或 UDF 名称冲突、UDF 定义不合法时抛出
     */
    @Builder
    public RuleEngine(@Singular List<RuleDefinition> ruleDefinitions, @Singular List<UdfDefinition> udfDefinitions) {
        this.register(new RuleDefinition(Constants.TRUE, Constants.TRUE, "SUCCESS"));
        this.register(new RuleDefinition(Constants.FALSE, Constants.FALSE, "FAILED"));
        this.register(new RuleDefinition(Constants.NULL, "Java.type('" + NaResult.class.getName() + "').DEFAULT", "NULL"));
        this.register(new RuleDefinition(Constants.NOP, "Java.type('" + NaResult.class.getName() + "').DEFAULT", "NOP"));
        ruleDefinitions.forEach(this::register);
        this.udfContainer = new UdfContainer(udfDefinitions);
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
     * @throws IllegalArgumentException 规则未注册或不是原子规则时抛出
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
     * @throws IllegalArgumentException 规则不是原子规则时抛出
     */
    public Object evalRule(String ruleId, Object root, Object context, Map<String, Object> arguments) {
        RuleDefinition ruleDefinition = this.ruleDefinitions.get(ruleId);
        if (ruleDefinition == null) {
            throw new RuleNotFoundException(ruleId);
        }
        Script script = compiledRules.get(ruleId);
        if (script == null) {
            throw new IllegalArgumentException("rule is not atomic: " + ruleId);
        }
        return doEval(script, root, context, arguments);
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
     * 在独立 JavaScript 作用域中执行已编译脚本
     *
     * @param script      已编译脚本
     * @param root        规则计算的目标对象
     * @param ruleContext 规则执行上下文
     * @param arguments   当前规则调用绑定的参数对象
     * @return 转换为 Java 对象的脚本返回值
     */
    private Object doEval(Script script, Object root, Object ruleContext, Map<String, Object> arguments) {
        return JsRuntime.execute((context, scope) -> {
            udfContainer.bind(context, scope);
            ScriptableObject.putProperty(scope, "$", Context.javaToJS(root, scope));
            ScriptableObject.putProperty(scope, "$$", Context.javaToJS(ruleContext, scope));
            ScriptableObject.putProperty(scope, "$args",
                    Context.javaToJS(arguments == null ? Map.of() : arguments, scope));
            return JsRuntime.toJava(script.exec(context, scope));
        });
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
            compiledRules.put(definition.getRuleId(), compile(definition.getExpression()));
        }
        compileDesc(definition.getDesc());
    }

    /**
     * 获取或编译 JavaScript 规则脚本
     *
     * @param expression JavaScript 表达式
     * @return 已编译的脚本
     */
    private Script compile(String expression) {
        return compiledScripts.computeIfAbsent(expression, this::compileExpression);
    }

    /**
     * 编译 JavaScript 规则表达式
     * <p>
     * 顶层对象字面量会按代码块解析，首尾为大括号时补充分组括号
     *
     * @param expression JavaScript 表达式
     * @return 已编译的脚本
     */
    private Script compileExpression(String expression) {
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
     * @return 已编译的描述模板脚本
     */
    private Script compileDesc(String originDesc) {
        return compiledDesc.computeIfAbsent(originDesc,
                desc -> doCompile("String.raw`" + desc + "`"));
    }

    /**
     * 编译 JavaScript 脚本
     *
     * @param expression JavaScript 源码
     * @return 已编译的脚本
     */
    private Script doCompile(String expression) {
        return JsRuntime.compile(
                expression, "rule-" + SOURCE_SEQUENCE.incrementAndGet());
    }
}
