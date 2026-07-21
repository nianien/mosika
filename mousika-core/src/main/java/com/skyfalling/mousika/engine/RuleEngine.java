package com.skyfalling.mousika.engine;

import com.skyfalling.mousika.eval.result.NaResult;
import com.skyfalling.mousika.utils.Constants;
import com.skyfalling.mousika.utils.JsRuntime;
import com.skyfalling.mousika.utils.JsonUtils;
import lombok.Builder;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 规则引擎
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Slf4j
public class RuleEngine {

    private static final Pattern DESC_PARAMETER = Pattern.compile("\\{(\\$+\\..+?)\\}");


    /**
     * 规则定义
     */
    private Map<String, RuleDefinition> ruleDefinitions = new HashMap<>();
    /**
     * 编译规则, key=expression, value=Source
     */
    private final ConcurrentMap<String, Source> compiledScripts = new ConcurrentHashMap<>();
    /**
     * 编译描述, key=desc, value=Source
     */
    private final ConcurrentMap<String, Source> compiledDesc = new ConcurrentHashMap<>();
    /**
     * 编译后的UDF
     */
    private Map<String, Object> compiledUdfs;

    @Builder
    public RuleEngine(@Singular List<RuleDefinition> ruleDefinitions, @Singular List<UdfDefinition> udfDefinitions) {
        //添加默认规则定义
        this.register(new RuleDefinition(Constants.TRUE, Constants.TRUE, "SUCCESS"));
        this.register(new RuleDefinition(Constants.FALSE, Constants.FALSE, "FAILED"));
        this.register(new RuleDefinition(Constants.NULL, "Java.type('" + NaResult.class.getName() + "').DEFAULT", "NULL"));
        this.register(new RuleDefinition(Constants.NOP, "Java.type('" + NaResult.class.getName() + "').DEFAULT", "NOP"));
        ruleDefinitions.forEach(this::register);
        this.compiledUdfs = new UdfContainer(udfDefinitions).compile();
    }

    /**
     * 执行规则
     *
     * @param ruleId 规则名
     */
    public Object evalRule(String ruleId, Object root, Object context) {
        RuleDefinition ruleDefinition = this.ruleDefinitions.get(ruleId);
        if (ruleDefinition == null) {
            throw new IllegalArgumentException("unregistered rule:" + ruleId);
        }
        return doEval(compile(ruleDefinition.getExpression()), root, context);
    }

    /**
     * 解析规则描述
     */
    public String evalRuleDesc(String ruleId, Object root, Object context) {
        RuleDefinition ruleDefinition = this.ruleDefinitions.get(ruleId);
        if (ruleDefinition == null) {
            throw new IllegalArgumentException("unregistered rule:" + ruleId);
        }
        return (String) doEval(compileDesc(ruleDefinition.getDesc()), root, context);
    }

    /**
     * 执行表达式
     */
    public Object evalExpr(String expression, Object root, Object context) {
        return doEval(compile(expression), root, context);
    }


    /**
     * 执行编译脚本
     */
    private Object doEval(Source script, Object root, Object ruleContext) {
        try (Context context = JsRuntime.createContext()) {
            Value bindings = context.getBindings(JsRuntime.LANGUAGE_ID);
            bindings.putMember("$", root);
            bindings.putMember("$$", ruleContext);
            compiledUdfs.forEach(bindings::putMember);
            return JsRuntime.toJava(context.eval(script));
        }
    }

    /**
     * 注册规则
     */
    private void register(RuleDefinition definition) {
        if (ruleDefinitions.containsKey(definition.getRuleId())) {
            throw new IllegalArgumentException("duplicate function defined: " + definition.getRuleId());
        }
        ruleDefinitions.put(definition.getRuleId(), definition);
        if (definition.getUseType() != 2) {
            //符合规则不编译
            compile(definition.getExpression());
        }
        compileDesc(definition.getDesc());
    }

    private Source compile(String expression) {
        return compiledScripts.computeIfAbsent(expression, this::doCompile);
    }


    /**
     * 编译规则描述，形如:{$.agentId}格式的表达式支持参数代入<p/>
     *
     * @param originDesc
     * @return
     */
    private Source compileDesc(String originDesc) {
        Matcher matcher = DESC_PARAMETER.matcher(originDesc);
        StringBuilder expression = new StringBuilder();
        int start = 0;
        while (matcher.find()) {
            expression.append(JsonUtils.toJson(originDesc.substring(start, matcher.start())))
                    .append('+')
                    .append(matcher.group(1))
                    .append('+');
            start = matcher.end();
        }
        expression.append(JsonUtils.toJson(originDesc.substring(start)));
        return compiledDesc.computeIfAbsent(expression.toString(), this::doCompile);
    }


    /**
     * 编译JS脚本
     *
     * @param expression
     * @return
     */
    private Source doCompile(String expression) {
        Source source = JsRuntime.createSource(expression, "rule-" + Integer.toHexString(expression.hashCode()));
        try (Context context = JsRuntime.createContext()) {
            context.parse(source);
        }
        return source;
    }


}
