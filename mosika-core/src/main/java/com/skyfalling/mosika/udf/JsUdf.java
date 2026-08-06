package com.skyfalling.mosika.udf;

import com.skyfalling.mosika.utils.JsRuntime;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.function.Function;

/**
 * 把 JavaScript 函数源码适配为规则引擎可调用的 UDF
 * <p>
 * 构造期间校验源码语法、可执行性和具名函数名称
 * 执行期间每个线程使用独立的 GraalJS {@link Context} 和函数对象
 * <p>
 * 支持函数表达式、箭头函数和包含同名函数声明的脚本
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * @since 2023/12/8
 * Copyright (c) 2004-2029 All Rights Reserved
 **/
public class JsUdf implements Function<Object[], Object> {

    /** 规则引擎中注册的函数名称 */
    private final String registeredName;

    /** 已完成语法编译并保证求值结果为函数的 JavaScript 源码 */
    private final Source functionSource;


    /**
     * 同一线程内由所有 {@link JsUdf} 实例共享的 GraalJS 上下文
     */
    private static final ThreadLocal<Context> CONTEXT_FACTORY = ThreadLocal.withInitial(JsRuntime::createContext);
    /**
     * 当前 UDF 在每个线程中独立求值得到的可执行函数对象
     */
    private final ThreadLocal<Value> functionFactory = ThreadLocal.withInitial(this::createFunctionObject);


    /**
     * 创建 JavaScript 函数 UDF
     *
     * @param registeredName 注册名称
     * @param source         求值结果为函数的 JavaScript 源码
     * @throws IllegalArgumentException 源码无法编译、结果不可执行或具名函数名称不一致时抛出
     */
    public JsUdf(String registeredName, String source) {
        this.registeredName = registeredName;
        this.functionSource = compileFunction(registeredName, source);
    }

    /**
     * 调用当前线程中的 JavaScript 函数并把 Polyglot 返回值转换为 Java 对象
     *
     * @param objects 传递给 JavaScript 函数的位置参数
     * @return 转换后的 Java 返回值
     */
    @Override
    public Object apply(Object... objects) {
        return JsRuntime.toJava(functionFactory.get().execute(objects));
    }

    /**
     * 在当前线程的 GraalJS 上下文中创建函数对象
     *
     * @return 已校验为可执行函数的 Polyglot 值
     */
    private Value createFunctionObject() {
        Context context = CONTEXT_FACTORY.get();
        return requireFunction(registeredName, context.eval(functionSource));
    }

    /**
     * 编译并校验 JavaScript 函数源码
     * <p>
     * 先按函数表达式解析，语法错误时再按包含同名函数声明的脚本解析
     * 非语法异常直接转换为 UDF 编译异常
     *
     * @param registeredName 注册名称
     * @param source         JavaScript 函数源码
     * @return 可在任意同语言上下文中重新求值的已编译源码
     * @throws IllegalArgumentException 两种格式均无法得到合法函数时抛出
     */
    private static Source compileFunction(String registeredName, String source) {
        Source functionExpression = JsRuntime.createSource(
                "(\n" + trimTrailingSemicolons(source) + "\n)", "udf-" + registeredName);
        try {
            validate(registeredName, functionExpression);
            return functionExpression;
        } catch (PolyglotException e) {
            if (!e.isSyntaxError()) {
                throw invalidFunction(registeredName, e);
            }
        }

        Source legacyScript = JsRuntime.createSource("(function () {\n"
                + source + "\n"
                + "return typeof " + registeredName + " === 'function' ? " + registeredName + " : null;\n"
                + "})()", "udf-" + registeredName);
        try {
            validate(registeredName, legacyScript);
            return legacyScript;
        } catch (PolyglotException e) {
            throw invalidFunction(registeredName, e);
        }
    }

    /**
     * 在临时上下文中验证源码求值结果
     *
     * @param registeredName 注册名称
     * @param source         待验证的已编译源码
     * @throws IllegalArgumentException 求值结果不是合法函数时抛出
     * @throws PolyglotException        JavaScript 求值失败时抛出
     */
    private static void validate(String registeredName, Source source) {
        try (Context context = JsRuntime.createContext()) {
            requireFunction(registeredName, context.eval(source));
        }
    }

    /**
     * 校验 Polyglot 值是否可执行，并核对非匿名函数的声明名称
     *
     * @param registeredName 注册名称
     * @param function       待校验的 Polyglot 值
     * @return 原始可执行函数值
     * @throws IllegalArgumentException 值不可执行或具名函数名称不一致时抛出
     */
    private static Value requireFunction(String registeredName, Value function) {
        if (function == null || !function.canExecute()) {
            throw new IllegalArgumentException("JavaScript UDF is not executable: " + registeredName);
        }
        Value name = function.getMember("name");
        if (name != null && name.isString()) {
            String declaredName = name.asString();
            if (!declaredName.isEmpty() && !registeredName.equals(declaredName)) {
                throw new IllegalArgumentException("JavaScript UDF name mismatch: registered as "
                        + registeredName + " but declared as " + declaredName);
            }
        }
        return function;
    }

    /**
     * 去掉函数表达式末尾多余的分号
     *
     * @param source 原始 JavaScript 源码
     * @return 去掉首尾空白和末尾分号后的源码
     */
    private static String trimTrailingSemicolons(String source) {
        String result = source.trim();
        while (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    /**
     * 创建保留 Polyglot 根因的统一编译异常
     *
     * @param registeredName 注册名称
     * @param cause          JavaScript 编译或求值异常
     * @return 对外暴露的参数异常
     */
    private static IllegalArgumentException invalidFunction(String registeredName, PolyglotException cause) {
        return new IllegalArgumentException("JavaScript UDF compile failed: " + registeredName, cause);
    }

}
