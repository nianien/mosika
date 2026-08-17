package com.nianien.mosika.udf;

import com.nianien.mosika.utils.JsRuntime;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 把 JavaScript 函数源码适配为规则引擎可调用的 UDF
 * <p>
 * 构造期间校验源码语法、可执行性和具名函数名称，执行期间绑定到当前规则作用域
 * <p>
 * 源码必须是求值结果为函数的单个 JavaScript 表达式，支持以下形式：
 * <pre>{@code
 * (a, b) => a + b
 *
 * function (a, b) {
 *     return a + b;
 * }
 *
 * function sum(a, b) {
 *     return a + b;
 * }
 * }</pre>
 * 具名函数的名称必须与注册名称一致。不支持辅助函数、变量声明加入口函数等脚本形式。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * @since 2023/12/8
 * Copyright (c) 2004-2029 All Rights Reserved
 **/
public class JsUdf {

    private static final AtomicLong SOURCE_SEQUENCE = new AtomicLong();

    private final String registeredName;

    private final Script functionScript;

    public JsUdf(String registeredName, String source) {
        this.registeredName = registeredName;
        this.functionScript = compileFunction(registeredName, source);
    }

    public Function bind(Context context, Scriptable scope) {
        return requireFunction(registeredName, functionScript.exec(context, scope));
    }

    private static Script compileFunction(String registeredName, String source) {
        String sourceName = "udf-" + registeredName + "-" + SOURCE_SEQUENCE.incrementAndGet();
        Script functionScript;
        try {
            functionScript = JsRuntime.compile(
                    "(\n" + trimTrailingSemicolons(source) + "\n)", sourceName);
        } catch (EvaluatorException e) {
            try {
                functionScript = JsRuntime.compile("(function () {\n"
                        + source + "\n"
                        + "return typeof " + registeredName + " === 'function' ? "
                        + registeredName + " : null;\n"
                        + "})()", sourceName);
            } catch (RhinoException legacyException) {
                throw new IllegalArgumentException(
                        "JavaScript UDF compile failed: " + registeredName, legacyException);
            }
        }
        Script validatedScript = functionScript;
        try {
            JsRuntime.execute((context, scope) ->
                    requireFunction(registeredName, validatedScript.exec(context, scope)));
            return validatedScript;
        } catch (RhinoException e) {
            throw new IllegalArgumentException(
                    "JavaScript UDF compile failed: " + registeredName, e);
        }
    }

    private static Function requireFunction(String registeredName, Object value) {
        if (!(value instanceof Function function)) {
            throw new IllegalArgumentException("JavaScript UDF is not executable: " + registeredName);
        }
        Object name = ScriptableObject.getProperty(function, "name");
        if (name != Scriptable.NOT_FOUND && !Undefined.isUndefined(name)) {
            String declaredName = Context.toString(name);
            if (!declaredName.isEmpty() && !registeredName.equals(declaredName)) {
                throw new IllegalArgumentException("JavaScript UDF name mismatch: registered as "
                        + registeredName + " but declared as " + declaredName);
            }
        }
        return function;
    }

    private static String trimTrailingSemicolons(String source) {
        String result = source.trim();
        while (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }
}
