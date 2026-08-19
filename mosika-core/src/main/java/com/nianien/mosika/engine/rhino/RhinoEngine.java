package com.nianien.mosika.engine.rhino;

import com.nianien.mosika.eval.context.UdfContext;
import com.nianien.mosika.udf.UdfContainer;
import com.nianien.mosika.udf.UdfDefinition;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Callable;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;

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
        BaseFunction typeFunction = nativeFunction(scope, (callContext, callScope, thisObject, arguments) -> {
            try {
                String className = Context.toString(arguments[0]);
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                Class<?> type = Class.forName(className, true, classLoader);
                return new NativeJavaClass(callScope, type);
            } catch (ClassNotFoundException e) {
                throw Context.throwAsScriptRuntimeEx(e);
            }
        });
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
    public static Object compile(String source) {
        return CONTEXT_FACTORY.call(context ->
                context.compileString(source, "script-" + SOURCE_SEQUENCE.incrementAndGet(), 1, null));
    }

    /**
     * 构建绑定了 UDF 的共享作用域:用 {@link RhinoUdfCompiler} 编译 {@code udfs} 的对象图(分组为
     * ByteBuddy Java 对象,叶子在此一次性编译成可调用值),绑入后封闭以便多线程并发只读复用。
     * <p>
     * 分组 Java 对象由 Rhino 原生按字段导航;字段值即已编译的叶子,无需运行期再包装。
     *
     * @param udfs UDF 容器
     * @return 已封闭的共享作用域,可作为 {@link #evaluate} 的原型
     */
    public static Object sharedScope(UdfContainer udfs) {
        return CONTEXT_FACTORY.call(context -> {
            NativeObject scope = new NativeObject();
            scope.setPrototype(GLOBAL_SCOPE);
            scope.setParentScope(null);
            udfs.compile(new RhinoUdfCompiler(context, scope)).forEach((name, value) ->
                    ScriptableObject.putProperty(scope, name, Context.javaToJS(value, scope)));
            scope.sealObject();
            return scope;
        });
    }

    /**
     * 在以 {@code scope} 为原型的一次性子作用域中注入 {@code $}/{@code $$}/{@code $args} 并求值,返回普通 Java 值。
     * <p>
     * 绑定与脚本产生的全局都落在这个隔离子作用域;共享内容(如 UDF)经原型链解析到 {@code scope}。
     * 若当前线程已进入 Rhino {@link Context}(见 {@link #inContext}),复用之,不再新建——同一次规则流的
     * 多段脚本共享一个 Context,省去每段新建 Context 的开销。
     *
     * @param script      已编译脚本
     * @param scope       作为原型的共享作用域(见 {@link #sharedScope})
     * @param root        {@code $} 绑定,允许 {@code null}
     * @param ruleContext {@code $$} 绑定,允许 {@code null}
     * @param args        {@code $args} 绑定,允许 {@code null}
     * @return 转换为普通 Java 对象的脚本返回值
     */
    public static Object evaluate(Object script, Object scope,
                                  Object root, Object ruleContext, Object args) {
        Context current = Context.getCurrentContext();
        if (current != null) {
            return doEvaluate(current, script, scope, root, ruleContext, args);
        }
        return CONTEXT_FACTORY.call(context -> doEvaluate(context, script, scope, root, ruleContext, args));
    }

    private static Object doEvaluate(Context context, Object script, Object scope,
                                     Object root, Object ruleContext, Object args) {
        NativeObject local = new NativeObject();
        local.setPrototype((Scriptable) scope);
        local.setParentScope(null);
        local.defineProperty("globalThis", local, ScriptableObject.DONTENUM);
        ScriptableObject.putProperty(local, "$", Context.javaToJS(root, local));
        ScriptableObject.putProperty(local, "$$", Context.javaToJS(ruleContext, local));
        ScriptableObject.putProperty(local, "$args", Context.javaToJS(args, local));
        return toJava(((Script) script).exec(context, local));
    }

    /**
     * 在单个 Rhino {@link Context} 内运行 {@code action}:当前线程未进入 Context 时进入一次并在结束后退出,
     * 已进入则直接复用。用于把一次规则流的多段 {@link #evaluate} 收敛到同一个 Context 下。
     * <p>
     * 并行分支在其它线程执行,各自独立进入 Context,不受此影响。
     *
     * @param action 要在 Context 内运行的动作
     * @return {@code action} 的返回值
     */
    public static <T> T inContext(Supplier<T> action) {
        if (Context.getCurrentContext() != null) {
            return action.get();
        }
        return CONTEXT_FACTORY.call(context -> action.get());
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

    /**
     * 把 {@link Callable} 包装为以 {@code scope} 为宿主的 Rhino 脚本函数。
     * 收敛 {@code Java.type}、{@code put} 等各处内置函数的构造样板:函数对象自身挂在 {@code scope}
     * 上,而 {@code call} 收到的是运行期作用域,由 {@code body} 决定如何使用。
     */
    public static BaseFunction nativeFunction(Scriptable scope, Callable body) {
        return new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope)) {
            @Override
            public Object call(Context context,
                               Scriptable callScope,
                               Scriptable thisObject,
                               Object[] arguments) {
                return body.call(context, callScope, thisObject, arguments);
            }
        };
    }

    // ────────────────────────────── 引擎基建 ──────────────────────────────

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

    /**
     * Java 值跨入脚本时按对象角色分派包装(顺序敏感:{@link UdfContext} 先于 {@link Map}):
     * <ul>
     *   <li>{@link UdfContext}(脚本的 {@code $$})→ {@link RuleNativeJavaObject};</li>
     *   <li>{@link Map}(脚本的 {@code $}/{@code $args} 及嵌套 Map)→ {@link RuleNativeJavaMap};</li>
     *   <li>其余对象 → 默认 {@link NativeJavaObject}。</li>
     * </ul>
     * 仅 {@code Map} 需要定制:{@code NativeJavaMap} 把数据 key 与方法名挤在同一属性命名空间,二者撞名
     * (详见 {@link RuleNativeJavaMap})。其余对象的属性即其成员,无此问题,保持默认 Java 语义。
     */
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

    // ────────────────────────────── UDF 绑定 ──────────────────────────────

    /**
     * UDF 叶子的 Rhino 编译回调:JS 源码编译绑定为脚本函数,Java UDF 包成"可调用+成员访问"对象。
     * 分组由 {@link UdfContainer} 用 ByteBuddy 组装。
     */
    private record RhinoUdfCompiler(Context context, Scriptable scope) implements UdfContainer.UdfCompiler {

        @Override
        public Object compile(UdfDefinition definition) {
            Object udf = definition.getUdf();
            if (udf instanceof String source) {
                return new JsUdf(definition.getName(), source).bind(context, scope);
            }
            return new JavaUdfFunction(udf, scope);
        }
    }

    // ─────────────────────────── Java 宿主包装 ───────────────────────────

    /**
     * 包装可变上下文 {@link UdfContext}(脚本的 {@code $$})。
     * <p>
     * {@code UdfContext} 是普通 Java 对象,不涉及 {@link RuleNativeJavaMap} 的 key/方法撞名,方法直接可用。
     * 唯一定制是 {@code put}:写入 {@code $$} 的值会驻留 Java 侧供后续读取,须先 {@link #toJava} 归一化——
     * 其 value 形参为 {@code Object},Rhino 不会自动深转。
     */
    private static final class RuleNativeJavaObject extends NativeJavaObject {

        private final BaseFunction putFunction;

        private RuleNativeJavaObject(Scriptable scope,
                                     UdfContext udfContext,
                                     TypeInfo staticType) {
            super(scope, udfContext, staticType);
            this.putFunction = nativeFunction(scope, (context, callScope, thisObject, arguments) -> {
                udfContext.put(Context.toString(arguments[0]), toJava(arguments[1]));
                return Undefined.instance;
            });
        }

        @Override
        public Object get(String name, Scriptable start) {
            if ("put".equals(name)) {
                return putFunction;
            }
            return super.get(name, start);
        }
    }

    /**
     * 把 Java {@link Map} 作为数据袋暴露给脚本:属性只映射数据 key,不回退到 Java 方法。
     * <p>
     * 读 {@code m.k}/{@code m['k']} 命中 key 才有值,否则为 {@code undefined};写 {@code m[k]=v} 经
     * {@link #toJava} 归一化后存入。这样规避 {@code NativeJavaMap} 把数据 key 与 {@code Map}/{@code Object}
     * 方法名混在同一属性命名空间的三类问题:读一个不存在、但与方法同名的 key(如 {@code $.size})得到方法
     * 对象,{@code $.class} 暴露反射,{@code putAll} 等绕过归一化写入。需要底层 Map 方法时经
     * {@link Wrapper#unwrap()} 取回。
     */
    private static final class RuleNativeJavaMap extends NativeJavaMap {

        private final Map<Object, Object> map;

        private RuleNativeJavaMap(Scriptable scope, Map<?, ?> map, TypeInfo staticType) {
            super(scope, map, staticType);
            this.map = (Map<Object, Object>) map;
        }

        @Override
        public Object get(String name, Scriptable start) {
            return map.containsKey(name) ? super.get(name, start) : Scriptable.NOT_FOUND;
        }

        @Override
        public Object get(int index, Scriptable start) {
            return map.containsKey(index) ? super.get(index, start) : Scriptable.NOT_FOUND;
        }

        @Override
        public boolean has(String name, Scriptable start) {
            return map.containsKey(name);
        }

        @Override
        public boolean has(int index, Scriptable start) {
            return map.containsKey(index);
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
