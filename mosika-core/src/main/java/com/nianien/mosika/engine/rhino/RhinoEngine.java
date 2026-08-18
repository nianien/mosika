package com.nianien.mosika.engine.rhino;

import com.nianien.mosika.eval.context.UdfContext;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * 规则内核的 Rhino JavaScript 执行引擎。
 * <p>
 * 负责编译脚本、构建共享作用域、在隔离子作用域中求值,以及 Rhino 值到普通 Java 值的转换。
 * 编译结果跨线程共享;共享作用域绑定一次并封闭,每次求值以其为原型建独立子作用域,
 * 隔离本次的 {@code $}/{@code $$}/{@code $args} 与脚本产生的全局。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public final class RhinoEngine {

    private static final AtomicLong SOURCE_SEQUENCE = new AtomicLong();

    private static final WrapFactory WRAP_FACTORY = new RuleWrapFactory();

    private static final RhinoContextFactory CONTEXT_FACTORY = new RhinoContextFactory();

    static {
        WRAP_FACTORY.setJavaPrimitiveWrap(false);
    }

    /**
     * 根作用域:封闭且跨线程共享,提供标准对象与宿主类型入口 {@code Java.type}。
     */
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

    private RhinoEngine() {
    }

    /**
     * 预编译 JS 源码为可跨线程复用的脚本。
     *
     * @param source JS 源码
     * @return 已编译脚本
     */
    public static Script compile(String source) {
        return CONTEXT_FACTORY.call(context ->
                context.compileString(source, "script-" + SOURCE_SEQUENCE.incrementAndGet(), 1, null));
    }

    /**
     * 构建一个以 {@link #GLOBAL_SCOPE} 为原型的共享作用域,交由 {@code binder} 绑定跨求值复用的内容
     * (如 UDF),随后封闭以便多线程并发只读复用。
     *
     * @param binder 在作用域上绑定共享内容
     * @return 已封闭的共享作用域,可作为 {@link #evaluate} 的原型
     */
    public static Scriptable sharedScope(BiConsumer<Context, Scriptable> binder) {
        return CONTEXT_FACTORY.call(context -> {
            NativeObject scope = new NativeObject();
            scope.setPrototype(GLOBAL_SCOPE);
            scope.setParentScope(null);
            binder.accept(context, scope);
            scope.sealObject();
            return scope;
        });
    }

    /**
     * 在以 {@code scope} 为原型的一次性子作用域中注入 {@code bindings} 并求值,返回普通 Java 值。
     * <p>
     * {@code bindings}(如 {@code $}/{@code $$}/{@code $args})与脚本产生的全局都落在这个隔离子作用域;
     * 共享内容(如 UDF)经原型链解析到 {@code scope}。
     *
     * @param script   已编译脚本
     * @param scope    作为原型的共享作用域(见 {@link #sharedScope})
     * @param bindings 本次求值注入的绑定,允许 {@code null} 值
     * @return 转换为普通 Java 对象的脚本返回值
     */
    public static Object evaluate(Script script, Scriptable scope, Map<String, Object> bindings) {
        return CONTEXT_FACTORY.call(context -> {
            NativeObject local = new NativeObject();
            local.setPrototype(scope);
            local.setParentScope(null);
            local.defineProperty("globalThis", local, ScriptableObject.DONTENUM);
            bindings.forEach((name, value) ->
                    ScriptableObject.putProperty(local, name, Context.javaToJS(value, local)));
            return toJava(script.exec(context, local));
        });
    }

    /**
     * 在一个以 {@link #GLOBAL_SCOPE} 为原型的一次性子作用域中运行 {@code action},返回其结果。
     * <p>
     * 面向需要直接操作 Rhino {@link Context}/{@link Scriptable} 的低层场景,
     * 如 JS UDF 编译期的可执行性校验;热路径求值请用 {@link #evaluate}。
     */
    public static <T> T runInScope(BiFunction<Context, Scriptable, T> action) {
        return CONTEXT_FACTORY.call(context -> {
            NativeObject scope = new NativeObject();
            scope.setPrototype(GLOBAL_SCOPE);
            scope.setParentScope(null);
            scope.defineProperty("globalThis", scope, ScriptableObject.DONTENUM);
            return action.apply(context, scope);
        });
    }

    /**
     * 把 Rhino 值转换为普通 Java 值。
     *
     * @param value Rhino 值
     * @return 普通 Java 值
     */
    public static Object toJava(Object value) {
        if (value == null || Undefined.isUndefined(value)) {
            return null;
        }
        if (value instanceof Wrapper wrapper) {
            return wrapper.unwrap();
        }
        if (value instanceof Function function) {
            // JS UDF 作为参数传入时,预先适配为 java Function,使通用 UdfDelegate 无需感知 Rhino
            return new RhinoFunctionAdapter(function);
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
