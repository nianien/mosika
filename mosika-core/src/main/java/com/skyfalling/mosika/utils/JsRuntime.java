package com.skyfalling.mosika.utils;

import com.skyfalling.mosika.eval.context.UdfContext;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.NativeJavaMap;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrapFactory;
import org.mozilla.javascript.Wrapper;
import org.mozilla.javascript.lc.type.TypeInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Rhino运行时。编译结果跨线程共享，每次执行使用独立的JavaScript作用域。
 */
public final class JsRuntime {

    private static final WrapFactory WRAP_FACTORY = new RuleWrapFactory();

    private static final RhinoContextFactory CONTEXT_FACTORY = new RhinoContextFactory();

    static {
        WRAP_FACTORY.setJavaPrimitiveWrap(false);
    }

    private static final ScriptableObject GLOBAL_SCOPE = CONTEXT_FACTORY.call(context -> {
        ScriptableObject standardScope = context.initStandardObjects(null, true);
        NativeObject scope = new NativeObject();
        scope.setPrototype(standardScope);
        scope.setParentScope(null);
        NativeObject javaObject = new NativeObject();
        javaObject.setParentScope(scope);
        javaObject.setPrototype(ScriptableObject.getObjectPrototype(scope));
        BaseFunction typeFunction = new BaseFunction(
                scope, ScriptableObject.getFunctionPrototype(scope)) {
            @Override
            public Object call(Context context,
                               Scriptable scope,
                               Scriptable thisObject,
                               Object[] arguments) {
                try {
                    String className = Context.toString(arguments[0]);
                    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                    Class<?> type = Class.forName(className, true, classLoader);
                    return new NativeJavaClass(scope, type);
                } catch (ClassNotFoundException e) {
                    throw Context.throwAsScriptRuntimeEx(e);
                }
            }
        };
        typeFunction.sealObject();
        javaObject.defineProperty(
                "type", typeFunction, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
        javaObject.sealObject();
        scope.defineProperty(
                "Java", javaObject, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
        scope.sealObject();
        return scope;
    });

    private JsRuntime() {
    }

    public static Script compile(String code, String name) {
        return CONTEXT_FACTORY.call(context -> context.compileString(code, name, 1, null));
    }

    public static <T> T execute(BiFunction<Context, Scriptable, T> action) {
        return CONTEXT_FACTORY.call(context -> {
            NativeObject scope = new NativeObject();
            scope.setPrototype(GLOBAL_SCOPE);
            scope.setParentScope(null);
            scope.defineProperty("globalThis", scope, ScriptableObject.DONTENUM);
            return action.apply(context, scope);
        });
    }

    public static Object toJava(Object value) {
        if (value == null || Undefined.isUndefined(value)) {
            return null;
        }
        if (value instanceof Wrapper wrapper) {
            return wrapper.unwrap();
        }
        if (value instanceof NativeArray array) {
            int size = Math.toIntExact(array.getLength());
            List<Object> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                Object item = array.get(i, array);
                result.add(item == Scriptable.NOT_FOUND ? null : toJava(item));
            }
            return result;
        }
        if (value instanceof NativeObject object) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Object id : object.getIds()) {
                String key = String.valueOf(id);
                Object member = id instanceof Number number
                        ? object.get(number.intValue(), object)
                        : object.get(key, object);
                result.put(key, toJava(member));
            }
            return result;
        }
        if (value instanceof Double number
                && Double.isFinite(number)
                && number == Math.rint(number)) {
            if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                return number.intValue();
            }
            if (number >= Long.MIN_VALUE && number < 0x1.0p63) {
                return number.longValue();
            }
        }
        if (value instanceof CharSequence sequence) {
            return sequence.toString();
        }
        return value;
    }

    private static final class RhinoContextFactory extends ContextFactory {

        private RhinoContextFactory() {
        }

        @Override
        protected boolean hasFeature(Context context, int featureIndex) {
            return featureIndex == Context.FEATURE_THREAD_SAFE_OBJECTS
                    || featureIndex == Context.FEATURE_ENABLE_JAVA_MAP_ACCESS
                    || super.hasFeature(context, featureIndex);
        }

        @Override
        protected void onContextCreated(Context context) {
            context.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            context.setWrapFactory(WRAP_FACTORY);
        }
    }

    private static final class RuleWrapFactory extends WrapFactory {

        @Override
        public Scriptable wrapAsJavaObject(Context context,
                                           Scriptable scope,
                                           Object javaObject,
                                           TypeInfo staticType) {
            if (javaObject instanceof UdfContext udfContext) {
                return new RuleNativeJavaObject(scope, udfContext, staticType);
            }
            if (javaObject instanceof Map<?, ?> map) {
                return new RuleNativeJavaMap(scope, map, staticType);
            }
            return super.wrapAsJavaObject(context, scope, javaObject, staticType);
        }
    }

    private static final class RuleNativeJavaObject extends NativeJavaObject {

        private final BaseFunction putFunction;

        private RuleNativeJavaObject(Scriptable scope,
                                     UdfContext udfContext,
                                     TypeInfo staticType) {
            super(scope, udfContext, staticType);
            this.putFunction = new BaseFunction(
                    scope, ScriptableObject.getFunctionPrototype(scope)) {
                @Override
                public Object call(Context context,
                                   Scriptable scope,
                                   Scriptable thisObject,
                                   Object[] arguments) {
                    udfContext.put(Context.toString(arguments[0]), toJava(arguments[1]));
                    return Undefined.instance;
                }
            };
        }

        @Override
        public Object get(String name, Scriptable start) {
            if ("put".equals(name)) {
                return putFunction;
            }
            return super.get(name, start);
        }
    }

    private static final class RuleNativeJavaMap extends NativeJavaMap {

        private final Map<Object, Object> map;

        private final BaseFunction putFunction;

        private RuleNativeJavaMap(Scriptable scope, Map<?, ?> map, TypeInfo staticType) {
            super(scope, map, staticType);
            this.map = (Map<Object, Object>) map;
            this.putFunction = new BaseFunction(
                    scope, ScriptableObject.getFunctionPrototype(scope)) {
                @Override
                public Object call(Context context,
                                   Scriptable scope,
                                   Scriptable thisObject,
                                   Object[] arguments) {
                    Object previous = RuleNativeJavaMap.this.map.put(
                            toJava(arguments[0]), toJava(arguments[1]));
                    return Context.javaToJS(previous, scope);
                }
            };
        }

        @Override
        public Object get(String name, Scriptable start) {
            if ("put".equals(name) && !map.containsKey(name)) {
                return putFunction;
            }
            return super.get(name, start);
        }

        @Override
        public void put(String name, Scriptable start, Object value) {
            map.put(name, toJava(value));
        }

        @Override
        public void put(int index, Scriptable start, Object value) {
            map.put(index, toJava(value));
        }
    }
}
