package com.nianien.mosika.engine.rhino;

import com.nianien.mosika.udf.UdfDelegate;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Wrapper;

import static com.nianien.mosika.engine.rhino.RhinoEngine.nativeFunction;
import static com.nianien.mosika.engine.rhino.RhinoEngine.toJava;

/**
 * 把 Java UDF 绑定为兼具"可调用函数"和"对象成员访问"双重语义的脚本对象。
 * <p>
 * {@code udf(...)} 走 {@link #call} 进入 {@link UdfDelegate#apply};
 * {@code udf.someMethod(...)} 通过 {@link #get} 回退到原始对象的成员方法。
 */
public class JavaUdfFunction extends BaseFunction implements Wrapper {

    private final UdfDelegate<Object, Object> delegate;

    /** 原始 Java 对象的 Rhino 包装,用于成员方法查找并作为其调用目标 */
    private final Scriptable members;

    @SuppressWarnings("unchecked")
    public JavaUdfFunction(Object udf, Scriptable scope) {
        super(scope, ScriptableObject.getFunctionPrototype(scope));
        this.delegate = (UdfDelegate<Object, Object>) UdfDelegate.of(udf);
        this.members = (Scriptable) Context.javaToJS(udf, scope);
    }

    @Override
    public Object call(Context context,
                       Scriptable scope,
                       Scriptable thisObject,
                       Object[] arguments) {
        Object[] parameters = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Object value = toJava(arguments[i]);
            // JS UDF 作为参数传入时适配为 java Function,使通用 UdfDelegate 无需感知 Rhino
            parameters[i] = value instanceof Function fn ? new RhinoFunctionAdapter(fn) : value;
        }
        return Context.javaToJS(delegate.apply(parameters), scope);
    }

    /**
     * 先解析函数自身属性(prototype/length 及原型链上的 call/apply 等),未命中再回退到
     * 原始 Java 对象的成员,使 {@code udf.someMethod(...)} 可用。
     * <p>
     * 成员方法被绑定到 {@link #members} 调用,而不依赖 Rhino 的 thisObj 解包,
     * 因为 {@link #unwrap()} 必须返回 delegate 以支撑嵌套 UDF 的参数转换。
     */
    @Override
    public Object get(String name, Scriptable start) {
        Object own = super.get(name, start);
        if (own != Scriptable.NOT_FOUND) {
            return own;
        }
        Object member = members.get(name, members);
        if (member == Scriptable.NOT_FOUND) {
            return Scriptable.NOT_FOUND;
        }
        return member instanceof Callable callable ? bindToOriginal(callable) : member;
    }

    /** 把成员方法包成绑定原始对象的可调用函数,屏蔽 thisObj 解包差异 */
    private BaseFunction bindToOriginal(Callable callable) {
        return nativeFunction(getParentScope(), (context, callScope, thisObject, arguments) ->
                callable.call(context, callScope, members, arguments));
    }

    @Override
    public Object unwrap() {
        return delegate;
    }

    /**
     * 把 Rhino JavaScript 函数适配为 Java UDF 可接收的 {@code Function<Object[], Object>}。
     * <p>
     * 适配器不创建 Context,也不切换线程;只能在当前规则求值仍处于 Rhino Context 内时同步调用。
     */
    private static final class RhinoFunctionAdapter implements java.util.function.Function<Object[], Object> {

        private final Function function;

        private RhinoFunctionAdapter(Function function) {
            this.function = function;
        }

        @Override
        public Object apply(Object[] arguments) {
            Context context = Context.getCurrentContext();
            Scriptable scope = function.getParentScope();
            return toJava(function.call(context, scope, scope, arguments));
        }
    }
}
