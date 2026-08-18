package com.nianien.mosika.engine.rhino;

import com.nianien.mosika.udf.UdfDelegate;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Wrapper;

import java.util.Map;

/**
 * 把 {@code UdfContainer} 的引擎无关 UDF 树绑定到 Rhino 作用域。
 * <p>
 * 树的叶子:JS UDF 为源码字符串(在此编译),Java UDF 为 {@link UdfDelegate};分组为嵌套 Map。
 * Java UDF 被包成兼具"可调用函数"与"对象成员访问"双重语义的脚本对象。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public final class RhinoUdfBinder {

    private final Map<String, Object> tree;

    public RhinoUdfBinder(Map<String, Object> tree) {
        this.tree = tree;
    }

    public void bind(Context context, Scriptable scope) {
        tree.forEach((name, udf) ->
                ScriptableObject.putProperty(scope, name, bindUdf(context, scope, name, udf)));
    }

    @SuppressWarnings("unchecked")
    private Object bindUdf(Context context, Scriptable scope, String name, Object udf) {
        if (udf instanceof String source) {
            return new JsUdf(name, source).bind(context, scope);
        }
        if (udf instanceof UdfDelegate<?, ?> delegate) {
            return new JavaUdfFunction((UdfDelegate<Object, Object>) delegate, delegate.target(), scope);
        }
        NativeObject group = new NativeObject();
        group.setParentScope(scope);
        group.setPrototype(ScriptableObject.getObjectPrototype(scope));
        ((Map<String, Object>) udf).forEach((childName, value) ->
                ScriptableObject.putProperty(group, childName, bindUdf(context, scope, childName, value)));
        return group;
    }

    /**
     * 把 Java UDF 绑定为兼具"可调用函数"和"对象成员访问"双重语义的脚本对象。
     * <p>
     * {@code udf(...)} 走 {@link #call} 进入 {@link UdfDelegate#apply};
     * {@code udf.someMethod(...)} 通过 {@link #get} 回退到原始对象的成员方法。
     */
    private static final class JavaUdfFunction extends BaseFunction implements Wrapper {

        private final UdfDelegate<Object, Object> delegate;

        /** 原始 Java 对象的 Rhino 包装,用于成员方法查找并作为其调用目标 */
        private final Scriptable members;

        private JavaUdfFunction(UdfDelegate<Object, Object> delegate, Object original, Scriptable scope) {
            super(scope, ScriptableObject.getFunctionPrototype(scope));
            this.delegate = delegate;
            this.members = (Scriptable) Context.javaToJS(original, scope);
        }

        @Override
        public Object call(Context context,
                           Scriptable scope,
                           Scriptable thisObject,
                           Object[] arguments) {
            Object[] parameters = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                parameters[i] = RhinoEngine.toJava(arguments[i]);
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
            Scriptable scope = getParentScope();
            return new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope)) {
                @Override
                public Object call(Context context,
                                   Scriptable scope,
                                   Scriptable thisObject,
                                   Object[] arguments) {
                    return callable.call(context, scope, members, arguments);
                }
            };
        }

        @Override
        public Object unwrap() {
            return delegate;
        }
    }
}
