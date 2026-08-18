package com.nianien.mosika.engine.rhino;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

/**
 * 把 Rhino JavaScript 函数适配为 Java UDF 可接收的 {@code Function<Object[], Object>}。
 * <p>
 * 适配器不创建 Context,也不切换线程。它只能在当前规则求值仍处于 Rhino Context 内时同步调用;
 * Java UDF 不应保存该对象并在本次求值结束后调用。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
final class RhinoFunctionAdapter implements java.util.function.Function<Object[], Object> {

    /** 已绑定到当前规则作用域的 Rhino 函数。 */
    private final Function function;

    RhinoFunctionAdapter(Function function) {
        this.function = function;
    }

    @Override
    public Object apply(Object[] arguments) {
        /*
         * Java UDF 是从 Rhino 函数调用栈中同步进入的,因此这里直接取得当前 Context。
         * JsUdf.bind() 在规则作用域内求值函数表达式,因此函数的 parentScope 就是其绑定作用域。
         * call() 的 scope/thisObject 都用该作用域,以延续当前规则的全局名称查找。
         */
        Context context = Context.getCurrentContext();
        Scriptable scope = function.getParentScope();
        return RhinoEngine.toJava(function.call(context, scope, scope, arguments));
    }
}
