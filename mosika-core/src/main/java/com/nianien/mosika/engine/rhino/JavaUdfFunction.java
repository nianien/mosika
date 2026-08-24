package com.nianien.mosika.engine.rhino;

import com.nianien.mosika.udf.UdfDelegate;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Wrapper;

import java.util.HashMap;
import java.util.Map;

import static com.nianien.mosika.engine.rhino.RhinoEngine.nativeFunction;
import static com.nianien.mosika.engine.rhino.RhinoEngine.toJava;

/**
 * 把 Java UDF 绑定为兼具直接调用和受控成员调用语义的脚本对象
 * <p>
 * {@code udf(...)} 进入 {@link UdfDelegate#apply}
 * {@code udf.someMethod(...)} 只访问代理明确暴露的具名方法
 */
public class JavaUdfFunction extends BaseFunction implements Wrapper {

    private final UdfDelegate<Object, Object> delegate;

    /** 构造期按方法名创建并封闭的具名 UDF 函数 */
    private final Map<String, BaseFunction> boundMethods = new HashMap<>();

    /**
     * 创建一个绑定到指定 Rhino 作用域的 Java UDF 函数对象
     * <p>
     * 具名 UDF 方法不能覆盖 JavaScript 函数对象已有属性
     *
     * @param udf   Java UDF 对象或已准备的 UDF 代理
     * @param scope Rhino 作用域
     */
    @SuppressWarnings("unchecked")
    public JavaUdfFunction(Object udf, Scriptable scope) {
        super(scope, ScriptableObject.getFunctionPrototype(scope));
        this.delegate = (UdfDelegate<Object, Object>) UdfDelegate.of(udf);
        for (UdfDelegate.MethodInfo method : delegate.methods()) {
            String methodName = method.getName();
            if ("apply".equals(methodName) || boundMethods.containsKey(methodName)) {
                continue;
            }
            if (super.get(methodName, this) != Scriptable.NOT_FOUND) {
                throw new IllegalArgumentException(
                        "udf method conflicts with JavaScript function property: " + methodName);
            }
            BaseFunction function = bind(methodName);
            function.sealObject();
            boundMethods.put(methodName, function);
        }
    }

    @Override
    public Object call(Context context,
                       Scriptable scope,
                       Scriptable thisObject,
                       Object[] arguments) {
        return Context.javaToJS(delegate.apply(parameters(arguments)), scope);
    }

    /**
     * 先解析函数自身属性，未命中再返回构造期绑定的具名方法
     */
    @Override
    public Object get(String name, Scriptable start) {
        Object own = super.get(name, start);
        if (own != Scriptable.NOT_FOUND) {
            return own;
        }
        BaseFunction method = boundMethods.get(name);
        return method != null ? method : Scriptable.NOT_FOUND;
    }

    /**
     * 将具名 UDF 方法绑定为 Rhino 可调用函数
     */
    private BaseFunction bind(String name) {
        return nativeFunction(getParentScope(), (context, callScope, thisObject, arguments) ->
                Context.javaToJS(delegate.invoke(name, parameters(arguments)), callScope));
    }

    /**
     * 将 Rhino 实参转为 Java 值，并为作为参数传入的 JavaScript 函数建立适配器
     */
    private static Object[] parameters(Object[] arguments) {
        Object[] parameters = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Object value = toJava(arguments[i]);
            parameters[i] = value instanceof Function fn ? new RhinoFunctionAdapter(fn) : value;
        }
        return parameters;
    }

    @Override
    public Object unwrap() {
        return delegate;
    }

    /**
     * 把 Rhino JavaScript 函数适配为 Java UDF 可接收的 {@code Function<Object[], Object>}
     * <p>
     * 适配器不创建 Context 也不切换线程，只能在当前规则求值仍处于 Rhino Context 内时同步调用
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
