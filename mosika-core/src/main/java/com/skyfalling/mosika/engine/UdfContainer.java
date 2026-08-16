package com.skyfalling.mosika.engine;

import com.cudrania.core.utils.StringUtils;
import com.skyfalling.mosika.udf.JsUdf;
import com.skyfalling.mosika.udf.UdfDelegate;
import com.skyfalling.mosika.utils.JsRuntime;
import org.mozilla.javascript.BaseFunction;
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
        if (StringUtils.isNotEmpty(group)) {
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
            udf = new JsUdf(name, source);
        } else {
            udf = UdfDelegate.of(udf);
        }
        map.put(name, udf);
    }

    private Object bindUdf(Context context, Scriptable scope, Object udf) {
        if (udf instanceof JsUdf jsUdf) {
            return jsUdf.bind(context, scope);
        }
        if (!(udf instanceof Map)) {
            return new JavaUdfFunction((UdfDelegate<Object, Object>) udf, scope);
        }
        NativeObject group = new NativeObject();
        group.setParentScope(scope);
        group.setPrototype(ScriptableObject.getObjectPrototype(scope));
        ((Map<String, Object>) udf).forEach((name, value) ->
                ScriptableObject.putProperty(group, name, bindUdf(context, scope, value)));
        return group;
    }

    private static final class JavaUdfFunction extends BaseFunction implements Wrapper {

        private final UdfDelegate<Object, Object> delegate;

        private JavaUdfFunction(UdfDelegate<Object, Object> delegate, Scriptable scope) {
            super(scope, ScriptableObject.getFunctionPrototype(scope));
            this.delegate = delegate;
        }

        @Override
        public Object call(Context context,
                           Scriptable scope,
                           Scriptable thisObject,
                           Object[] arguments) {
            Object[] parameters = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                parameters[i] = JsRuntime.toJava(arguments[i]);
            }
            return Context.javaToJS(delegate.apply(parameters), scope);
        }

        @Override
        public Object unwrap() {
            return delegate;
        }
    }
}
