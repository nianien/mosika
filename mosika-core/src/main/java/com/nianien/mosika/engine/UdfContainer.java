package com.nianien.mosika.engine;

import com.nianien.mosika.udf.JsUdf;
import com.nianien.mosika.udf.UdfDelegate;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Wrapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * UDF容器，用于将UDF绑定为JavaScript命名空间对象
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * @since 2023/11/7
 * Copyright (c) 2004-2029 All Rights Reserved.
 **/
public class UdfContainer {

    private final Map<String, Object> udfDefined = new ConcurrentHashMap<>();

    public UdfContainer(List<UdfDefinition> udfDefinitions) {
        udfDefinitions.forEach(this::register);
    }

    public void bind(Context context, Scriptable scope) {
        udfDefined.forEach((name, udf) ->
                ScriptableObject.putProperty(scope, name, bindUdf(context, scope, udf)));
    }

    public void register(UdfDefinition udfDefinition) {
        register(udfDefinition.getGroup(), udfDefinition.getName(), udfDefinition.getUdf());
    }

    private void register(String group, String name, Object udf) {
        Map<String, Object> map = udfDefined;
        if (group != null && !group.isEmpty()) {
            String[] tokens = group.replaceAll("\\s", "").split("\\.+");
            int i = 0;
            for (; i < tokens.length; i++) {
                String token = tokens[i];
                Object value = map.computeIfAbsent(token, key -> new HashMap<>());
                if (value instanceof Map) {
                    map = (Map<String, Object>) value;
                } else {
                    String conflictName = Arrays.stream(tokens)
                            .limit(i + 1)
                            .collect(Collectors.joining("."));
                    throw new IllegalArgumentException("udf: " + conflictName + " is already defined!");
                }
            }
        }
        if (map.containsKey(name)) {
            throw new IllegalArgumentException("udf: " + name + " is already defined!");
        }
        if (udf instanceof String source) {
            map.put(name, new JsUdf(name, source));
        } else {
            map.put(name, new JavaUdf(udf, (UdfDelegate<Object, Object>) UdfDelegate.of(udf)));
        }
    }

    private Object bindUdf(Context context, Scriptable scope, Object udf) {
        if (udf instanceof JsUdf jsUdf) {
            return jsUdf.bind(context, scope);
        }
        if (udf instanceof JavaUdf javaUdf) {
            return new JavaUdfFunction(javaUdf.delegate(), javaUdf.original(), scope);
        }
        NativeObject group = new NativeObject();
        group.setParentScope(scope);
        group.setPrototype(ScriptableObject.getObjectPrototype(scope));
        ((Map<String, Object>) udf).forEach((name, value) ->
                ScriptableObject.putProperty(group, name, bindUdf(context, scope, value)));
        return group;
    }

    /**
     * 注册期保留的 Java UDF。
     * <p>
     * {@code original} 是注册时传入的原始对象，用于 {@code udf.someMethod(...)} 的成员访问；
     * {@code delegate} 是预解析的调用代理，用于 {@code udf(...)} 按 apply 签名分派。
     */
    private record JavaUdf(Object original, UdfDelegate<Object, Object> delegate) {
    }

    /**
     * 把 Java UDF 绑定为兼具"可调用函数"和"对象成员访问"双重语义的脚本对象。
     * <p>
     * {@code udf(...)} 走 {@link #call} 进入 {@link UdfDelegate#apply}；
     * {@code udf.someMethod(...)} 通过 {@link #get} 回退到原始对象的成员方法。
     */
    private static final class JavaUdfFunction extends BaseFunction implements Wrapper {

        private final UdfDelegate<Object, Object> delegate;

        /** 原始 Java 对象的 Rhino 包装，用于成员方法查找并作为其调用目标 */
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
         * 先解析函数自身属性（{@code prototype}/{@code length} 及原型链上的 {@code call}/{@code apply} 等），
         * 未命中再回退到原始 Java 对象的成员，使 {@code udf.someMethod(...)} 可用。
         * <p>
         * 成员方法被绑定到 {@link #members} 调用，而不依赖 Rhino 的 thisObj 解包，
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

        /** 把成员方法包成绑定原始对象的可调用函数，屏蔽 thisObj 解包差异 */
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
