package com.skyfalling.mosika.udf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.skyfalling.mosika.utils.JsonUtils;
import com.skyfalling.mosika.utils.JsRuntime;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Java UDF 的统一调用接口。
 * <p>
 * {@link Wrapper} 在注册期解析 UDF 的 {@code apply} 方法，在调用期根据实参数量选择方法，
 * 并按照方法声明的泛型参数类型完成 Rhino 值、嵌套 UDF 和普通 Java 对象之间的转换。
 *
 * @param <P> 参数类型
 * @param <R> 返回值类型
 */
@FunctionalInterface
public interface UdfDelegate<P, R> {


    /**
     * 调用 UDF。
     *
     * @param ps 参数列表
     * @return UDF 执行结果
     */
    R apply(P... ps);


    /**
     * 创建带参数转换和重载分派能力的 UDF 代理。
     *
     * @param udf 原始 Java UDF
     * @return UDF 代理
     */
    static UdfDelegate of(Object udf) {
        return new Wrapper(udf);
    }

    /**
     * Java UDF 代理。
     * <p>
     * 每个 Java UDF 在注册时创建一个代理。代理缓存按参数个数索引的方法签名，
     * 调用时不再遍历反射方法，只进行数组查询、参数转换和反射调用。
     * <p>
     * 当一个 UDF 作为另一个 Java UDF 的参数时，本类还负责保留其可调用语义：
     * Java UDF 可以还原为注册时的原始实例，也可以适配为
     * {@link java.util.function.Function}；JavaScript UDF 则通过
     * {@link RhinoFunctionAdapter} 适配为同一 Java 函数接口。
     * <p>
     * 只有参数声明为 {@code Function<Object[], Object>} 时才能同时接收 Java UDF
     * 和 JavaScript UDF。参数声明为 {@code Functions.Function0} 至
     * {@code Functions.Function22} 时只支持对应的原始 Java UDF 实例。
     * <p>
     * 例如下面的 Java UDF 接收待调用函数和可变参数，因此可以调用不同参数个数的
     * Java UDF 或 JavaScript UDF：
     * <pre>{@code
     * public class SumUdf
     *         implements Functions.Function2<Integer, Integer, Integer> {
     *
     *     @Override
     *     public Integer apply(Integer left, Integer right) {
     *         return left + right;
     *     }
     * }
     *
     * public class InvokeUdf
     *         implements Functions.Function2<
     *                 java.util.function.Function<Object[], Object>, Object[], Object> {
     *
     *     @Override
     *     public Object apply(java.util.function.Function<Object[], Object> function,
     *                         Object... arguments) {
     *         return function.apply(arguments);
     *     }
     * }
     *
     * RuleEngine engine = RuleEngine.builder()
     *         .udfDefinitions(java.util.List.of(
     *                 new UdfDefinition("math", "javaSum", new SumUdf()),
     *                 new UdfDefinition("math", "jsSum3", "(a, b, c) => a + b + c"),
     *                 new UdfDefinition("policy", "invoke", new InvokeUdf())))
     *         .build();
     *
     * engine.evalExpr("policy.invoke(math.javaSum, 1, 2)", null, null);    // 3
     * engine.evalExpr("policy.invoke(math.jsSum3, 1, 2, 3)", null, null); // 6
     * }</pre>
     *
     * Created on 2022/12/6
     *
     * @author skyfalling {@literal <skyfalling@live.com>}
     */

    class Wrapper implements UdfDelegate {

        /**
         * 把 Rhino 传入的实参转换为 Java UDF 方法声明的参数类型。
         * <p>
         * Java UDF 和 JavaScript UDF 是两种互斥的实参类型，两个特殊分支之间没有
         * 顺序要求，但都必须在普通 JSON 转换之前处理：
         * <ol>
         *     <li>作为参数传入的 Java UDF 根据目标类型还原或适配；</li>
         *     <li>作为参数传入的 JavaScript UDF 在目标类型为 Java Function 时适配；</li>
         *     <li>其余值沿用 JSON 转换，转换为方法声明的普通 Java 类型。</li>
         * </ol>
         * {@code t} 来自目标 {@code apply} 方法的泛型参数，因此同时检查裸
         * {@code Function} 和 {@code Function<Object[], Object>} 两种声明形式。
         */
        private static BiFunction<Object, Type, Object> converter = (s, t) -> {
            boolean javaFunction = t == java.util.function.Function.class
                    || t instanceof ParameterizedType parameterizedType
                    && parameterizedType.getRawType() == java.util.function.Function.class;

            /*
             * Java UDF 进入 Rhino 后由 JavaUdfFunction 包装；JsRuntime.toJava()
             * 会将其解包为当前 Wrapper。
             *
             * 目标参数是 java.util.function.Function 时，以 wrapper::apply 暴露统一的
             * Object[] 调用入口，使被调用 UDF 继续使用本类的参数转换和重载分派。
             *
             * 目标参数不是 java.util.function.Function 时，直接返回注册时的原始
             * Java UDF 实例，例如实现 Functions.Function2 的对象，从而保持原来的
             * 嵌套 Java UDF 调用方式。
             */
            if (s instanceof Wrapper wrapper) {
                if (javaFunction) {
                    return (java.util.function.Function<Object[], Object>) wrapper::apply;
                }
                return wrapper.udf;
            }

            /*
             * JavaScript UDF 在 Rhino 中是 org.mozilla.javascript.Function，并不实现
             * java.util.function.Function；调用它还需要 Rhino Context 和 Scriptable
             * 作用域。因此这里保存 Rhino 函数，在 Java UDF 调用 Function.apply()
             * 时由适配器补齐 Rhino 调用参数。
             */
            if (s instanceof Function function && javaFunction) {
                return new RhinoFunctionAdapter(function);
            }

            /*
             * 普通参数沿用已有的 JSON 类型转换。非字符串先序列化，字符串直接交给
             * JsonUtils，目标类型由被调用 apply 方法的反射签名决定。
             */
            if (!(s instanceof String)) {
                s = JsonUtils.toJson(s);
            }
            Object v = JsonUtils.toBean((String) s, new TypeReference<>() {
                @Override
                public Type getType() {
                    return t;
                }
            });
            return v != null ? v : s;
        };


        /** 被代理的原始 Java UDF 实例。 */
        private Object udf;

        /**
         * 按参数个数索引的 {@code apply} 方法签名。
         * 数组下标就是参数个数，没有对应重载的位置为 {@code null}。
         */
        private final Signature[] signatures;

        /** 可接收不定数量实参的 {@code apply} 方法签名。 */
        private final Signature varArgsSignature;

        /**
         * 创建代理并解析 UDF 支持的参数个数。
         *
         * @param udf 原始 Java UDF
         */
        public Wrapper(Object udf) {
            this.udf = udf;
            this.signatures = resolve(udf);
            Signature varArgs = null;
            for (Signature signature : signatures) {
                if (signature != null && signature.method.isVarArgs()) {
                    varArgs = signature;
                    break;
                }
            }
            this.varArgsSignature = varArgs;
        }


        /**
         * 根据实参数量调用对应的 {@code apply} 方法。
         * <p>
         * 方法签名在构造时已经解析完成。这里先按参数个数从数组中定位签名，
         * 没有固定参数签名时再尝试可变参数签名。可变参数方法的尾部实参会打包成
         * 方法声明的数组参数，再依据泛型参数类型转换每个实参并调用原始 UDF。
         *
         * @param params 调用参数
         * @return 原始 UDF 的执行结果
         * @throws RuntimeException 没有匹配的固定参数或可变参数 {@code apply} 方法时抛出
         */
        @Override
        public Object apply(Object... params) {
            Signature signature = params.length < signatures.length ? signatures[params.length] : null;
            if (signature == null
                    && varArgsSignature != null
                    && params.length >= varArgsSignature.parameterTypes.length - 1) {
                signature = varArgsSignature;
            }
            if (signature == null) {
                throw new RuntimeException("no compatible method has " + params.length + " parameters!");
            }
            if (signature.method.isVarArgs()) {
                int fixedCount = signature.parameterTypes.length - 1;
                Object[] invocationParams = new Object[signature.parameterTypes.length];
                System.arraycopy(params, 0, invocationParams, 0, fixedCount);
                Object[] variableParams = new Object[params.length - fixedCount];
                System.arraycopy(params, fixedCount, variableParams, 0, variableParams.length);
                invocationParams[fixedCount] = variableParams;
                params = invocationParams;
            }
            Object[] casts = convertParams(params, signature.parameterTypes);
            return invoke(signature.method, udf, casts);
        }

        /**
         * 解析非桥接的 {@code apply} 方法，并建立参数个数到方法签名的直接索引。
         * <p>
         * 泛型接口生成的桥接方法不代表额外重载，必须排除，否则会覆盖真实方法签名。
         *
         * @param udf 原始 Java UDF
         * @return 按参数个数索引的方法签名数组
         */
        private static Signature[] resolve(Object udf) {
            Collection<Method> methods = applyMethods(udf.getClass());
            int max = -1;
            for (Method method : methods) {
                max = Math.max(max, method.getParameterCount());
            }
            Signature[] result = new Signature[max + 1];
            for (Method method : methods) {
                int arity = method.getParameterCount();
                result[arity] = new Signature(method);
            }
            return result;
        }

        /**
         * 收集类及其父类型声明的名为 {@code apply} 的非桥接方法。
         * <p>
         * 合并 {@link Class#getMethods()}（含继承的 public 方法）与
         * {@link Class#getDeclaredMethods()}（本类声明的方法），去重后过滤，
         * 语义与原 {@code Reflections.getMethods} 一致。
         *
         * @param type UDF 实现类
         * @return 名为 {@code apply} 的非桥接方法集合
         */
        private static Collection<Method> applyMethods(Class<?> type) {
            Set<Method> methods = new LinkedHashSet<>();
            for (Method method : type.getMethods()) {
                methods.add(method);
            }
            for (Method method : type.getDeclaredMethods()) {
                methods.add(method);
            }
            methods.removeIf(method -> !method.getName().equals("apply") || method.isBridge());
            return methods;
        }

        /**
         * 按目标参数类型逐个转换实参。
         *
         * @param parameters     待转换的实参
         * @param parameterTypes 目标 {@code apply} 方法的泛型参数类型
         * @return 转换后的实参数组
         */
        private static Object[] convertParams(Object[] parameters, Type[] parameterTypes) {
            Object[] castParams = new Object[parameters.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                castParams[i] = doConvert(parameters[i], parameterTypes[i]);
            }
            return castParams;
        }

        /**
         * 把单个实参转换为目标类型。
         * <p>
         * 顺序与原 {@code Reflections.doConvert} 保持一致：{@code null} 直接返回；
         * 目标是 {@link Class} 且实参已是其实例时原样返回（该分支先于 {@link #converter}，
         * 决定了 Java UDF 作为 {@code Object} 参数时保留 {@code Wrapper} 而非解包）；
         * 字符串到原始类型和枚举按名称解析；其余情况交给 {@link #converter}。
         *
         * @param value 原始实参
         * @param type  目标参数类型
         * @return 转换后的值
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Object doConvert(Object value, Type type) {
            if (value == null) {
                return null;
            }
            if (type instanceof Class<?> clazz) {
                if (clazz.isInstance(value)) {
                    return value;
                }
                if (value instanceof String valueString) {
                    if (clazz.equals(String.class)) {
                        return valueString;
                    }
                    if (clazz.equals(Boolean.TYPE) || clazz.equals(Boolean.class)) {
                        return Boolean.valueOf(valueString);
                    }
                    if (clazz.equals(Byte.TYPE) || clazz.equals(Byte.class)) {
                        return Byte.valueOf(valueString);
                    }
                    if (clazz.equals(Short.TYPE) || clazz.equals(Short.class)) {
                        return Short.valueOf(valueString);
                    }
                    if (clazz.equals(Integer.TYPE) || clazz.equals(Integer.class)) {
                        return Integer.valueOf(valueString);
                    }
                    if (clazz.equals(Long.TYPE) || clazz.equals(Long.class)) {
                        return Long.valueOf(valueString);
                    }
                    if (clazz.equals(Float.TYPE) || clazz.equals(Float.class)) {
                        return Float.valueOf(valueString);
                    }
                    if (clazz.equals(Double.TYPE) || clazz.equals(Double.class)) {
                        return Double.valueOf(valueString);
                    }
                    if (clazz.equals(Character.TYPE) || clazz.equals(Character.class)) {
                        return Character.valueOf(valueString.charAt(0));
                    }
                    if (clazz.isEnum()) {
                        return Enum.valueOf((Class<Enum>) clazz, valueString);
                    }
                }
            }
            return converter.apply(value, type);
        }

        /**
         * 反射调用 UDF 方法。
         * <p>
         * 解包 {@link InvocationTargetException}，向上抛出 UDF 实际抛出的异常，
         * 使 UDF 内部异常按业务语义正常传播；反射本身的
         * {@link IllegalAccessException} 作为基础设施错误包装为 {@link IllegalStateException}。
         *
         * @param method     目标方法
         * @param target     UDF 实例
         * @param arguments  已转换的实参
         * @return 方法返回值
         */
        private static Object invoke(Method method, Object target, Object[] arguments) {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException e) {
                // FunctionN.apply 不声明受检异常，UDF 只会抛非受检异常，直接透传其真实异常
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(cause != null ? cause : e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * 一次 UDF 调用所需的反射信息。
         * <p>
         * 使用 {@link Type} 而不是 {@link Class}，以保留反射方法声明中可获得的
         * {@link ParameterizedType} 信息，例如 {@code Function<Object[], Object>}
         * 和带元素类型的集合。未解析的 {@link java.lang.reflect.TypeVariable}
         * 仍然只是类型变量，不在这里推导其具体类型。
         */
        private static final class Signature {

            private final Method method;
            private final Type[] parameterTypes;

            private Signature(Method method) {
                // 注册时一次性放开可达性，避免每次调用重复设置，并让不可访问的方法尽早暴露
                method.setAccessible(true);
                this.method = method;
                this.parameterTypes = method.getGenericParameterTypes();
            }
        }

        /**
         * 把 Rhino JavaScript 函数适配为 Java UDF 可接收的
         * {@code Function<Object[], Object>}。
         * <p>
         * 适配器不创建 Context，也不切换线程。它只能在当前规则求值仍处于 Rhino
         * Context 内时同步调用；Java UDF 不应保存该对象并在本次求值结束后调用。
         */
        private static final class RhinoFunctionAdapter
                implements java.util.function.Function<Object[], Object> {

            /** 已绑定到当前规则作用域的 Rhino 函数。 */
            private final Function function;

            private RhinoFunctionAdapter(Function function) {
                this.function = function;
            }

            @Override
            public Object apply(Object[] arguments) {
                /*
                 * Java UDF 是从 Rhino 函数调用栈中同步进入的，因此这里直接取得当前
                 * Context。JsUdf.bind() 在规则作用域内求值函数表达式，因此函数的
                 * parentScope 就是其绑定作用域。
                 *
                 * call() 的 scope 参数使用该作用域，以延续当前规则的全局名称查找；
                 * thisObject 也使用该作用域，用于确定普通函数中的 this。函数访问同组
                 * 或其他 UDF 依赖的是绑定时形成的作用域链，而不是 thisObject。
                 */
                Context context = Context.getCurrentContext();
                Scriptable scope = function.getParentScope();

                // Rhino 返回值统一转换为规则引擎使用的 Java 值。
                return JsRuntime.toJava(function.call(context, scope, scope, arguments));
            }
        }

    }
}
