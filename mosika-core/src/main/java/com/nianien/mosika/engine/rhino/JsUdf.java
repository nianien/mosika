package com.nianien.mosika.engine.rhino;


import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

/**
 * 把 JavaScript 函数源码适配为规则引擎可调用的 UDF
 * <p>
 * 构造期间校验源码语法、可执行性和具名函数名称，执行期间绑定到当前规则作用域
 * <p>
 * 源码优先按求值结果为函数的单个 JavaScript 表达式解析，支持以下形式
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
 * 同时兼容包含辅助函数或变量声明、并以注册同名函数为入口的历史脚本形式
 * 具名入口函数的名称必须与注册名称一致
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * @since 2023/12/8
 * Copyright (c) 2004-2029 All Rights Reserved
 **/
public class JsUdf {

    private final String registeredName;

    private final Script functionScript;

    public JsUdf(String registeredName, String source) {
        this.registeredName = registeredName;
        this.functionScript = compileFunction(registeredName, source);
    }

    public Function bind(Context context, Scriptable scope) {
        return requireFunction(registeredName, functionScript.exec(context, scope, scope));
    }

    private static Script compileFunction(String registeredName, String source) {
        Script functionScript;
        try {
            functionScript = (Script) RhinoEngine.compile("(\n" + trimTrailingSemicolons(source) + "\n)");
        } catch (EvaluatorException e) {
            try {
                functionScript = (Script) RhinoEngine.compile("(function () {\n"
                        + source + "\n"
                        + "return typeof " + registeredName + " === 'function' ? "
                        + registeredName + " : null;\n"
                        + "})()");
            } catch (RhinoException legacyException) {
                throw new IllegalArgumentException(
                        "JavaScript UDF compile failed: " + registeredName, legacyException);
            }
        }
        Script validatedScript = functionScript;
        try {
            RhinoEngine.runInScope((context, scope) ->
                    requireFunction(registeredName, validatedScript.exec(context, scope, scope)));
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
