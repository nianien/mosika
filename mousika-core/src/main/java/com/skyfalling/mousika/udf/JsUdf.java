package com.skyfalling.mousika.udf;

import com.skyfalling.mousika.utils.JsRuntime;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.function.Function;

/**
 *  支持JS 定义 UDF
 * @author skyfalling {@literal <skyfalling@live.com>}
 * @since 2023/12/8
 * Copyright (c) 2004-2029 All Rights Reserved.
 **/
public class JsUdf implements Function<Object[], Object> {


    private final String funcName;
    private final Source funcSource;


    /**
     * 类级线程副本
     */
    private static final ThreadLocal<Context> CONTEXT_FACTORY = ThreadLocal.withInitial(JsRuntime::createContext);
    /**
     * 实例级线程副本
     */
    private final ThreadLocal<Value> funcFactory = ThreadLocal.withInitial(this::createFuncObject);


    /**
     * JS函数UDF
     *
     * @param funcName 函数名称
     * @param funcBody 函数定义
     */
    public JsUdf(String funcName, String funcBody) {
        this.funcName = funcName;
        this.funcSource = JsRuntime.createSource("(function () {\n"
                + funcBody + "\n"
                + "return typeof " + funcName + " === 'function' ? " + funcName + " : null;\n"
                + "})()", "udf-" + funcName);
    }

    @Override
    public Object apply(Object... objects) {
        return JsRuntime.toJava(funcFactory.get().execute(objects));
    }

    private Value createFuncObject() {
        Context context = CONTEXT_FACTORY.get();
        Value function = context.eval(funcSource);
        if (function == null || !function.canExecute()) {
            throw new IllegalArgumentException("JavaScript UDF is not executable: " + funcName);
        }
        return function;
    }

}
