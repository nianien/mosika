package com.nianien.mosika.udf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.nianien.mosika.annotation.UdfMethod;
import com.nianien.mosika.utils.JsonUtils;
import com.nianien.mosika.utils.Reflections;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Java UDF 的受控调用代理
 * <p>
 * 代理只暴露带 {@link UdfMethod} 的方法，并按方法名和实参数量完成分派
 * {@code apply} 是直接函数调用入口，其他方法通过具名成员调用
 *
 * @param <P> 直接调用参数类型
 * @param <R> 直接调用返回类型
 */
public interface UdfDelegate<P, R> {

    /**
     * 以函数形式调用已暴露的 {@code apply} 方法
     *
     * @param ps 调用实参
     * @return UDF 执行结果
     */
    R apply(P... ps);

    /**
     * 按方法名调用已暴露的具名方法
     *
     * @param methodName 方法名
     * @param ps         调用实参
     * @return UDF 执行结果
     */
    Object invoke(String methodName, Object... ps);

    /**
     * 判断指定方法名是否包含已暴露签名
     *
     * @param methodName 方法名
     * @return 存在已暴露签名时返回 {@code true}
     */
    boolean contains(String methodName);

    /**
     * 获取运行时与展示层共用的暴露方法元数据
     *
     * @return 按方法名和形参个数排序的方法元数据
     */
    List<MethodInfo> methods();

    /**
     * 获取用于解析注解与方法签名的 UDF 类型
     *
     * @return 原始 UDF 类型或 Spring AOP 目标类型
    */
    Class<?> type();

    /**
     * 为普通 Java UDF 对象创建受控代理
     * <p>
     * 已经是 UDF 代理的对象会原样返回
     *
     * @param udf Java UDF 对象
     * @return UDF 代理
     */
    static UdfDelegate<?, ?> of(Object udf) {
        if (udf instanceof UdfDelegate delegate) {
            return delegate;
        }
        return new Wrapper(udf, udf.getClass(), Function.identity());
    }

    /**
     * 使用独立的元数据类型和调用方法映射创建受控代理
     * <p>
     * Spring AOP 使用目标类读取注解和泛型签名，同时将调用映射到代理 Bean
     *
     * @param udf                      实际调用对象
     * @param type                     元数据解析类型
     * @param invocationMethodResolver 调用方法映射器
     * @return UDF 代理
     */
    static UdfDelegate<?, ?> of(Object udf, Class<?> type,
                                Function<Method, Method> invocationMethodResolver) {
        if (udf instanceof UdfDelegate delegate) {
            return delegate;
        }
        return new Wrapper(udf, type, invocationMethodResolver);
    }

    /**
     * 已暴露方法的反射元数据
     * <p>
     * 参数和返回类型保留声明方法中可获取的泛型信息
     */
    final class MethodInfo {

        private final String name;
        private final Type[] parameterTypes;
        private final String[] parameterNames;
        private final Type returnType;
        private final boolean varArgs;
        private final String description;

        private MethodInfo(Method method, String description) {
            this.name = method.getName();
            this.parameterTypes = method.getGenericParameterTypes();
            Parameter[] parameters = method.getParameters();
            String[] names = new String[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                if (!parameters[i].isNamePresent()) {
                    names = new String[0];
                    break;
                }
                names[i] = parameters[i].getName();
            }
            this.parameterNames = names;
            this.returnType = method.getGenericReturnType();
            this.varArgs = method.isVarArgs();
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public Type[] getParameterTypes() {
            return parameterTypes;
        }

        public String[] getParameterNames() {
            return parameterNames;
        }

        public Type getReturnType() {
            return returnType;
        }

        public boolean isVarArgs() {
            return varArgs;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 基于反射签名的默认 UDF 代理实现
     * <p>
     * 代理构造时一次性解析并索引全部暴露方法，调用时不再遍历反射方法
     */
    class Wrapper implements UdfDelegate {

        /**
         * 参数基础转换未命中时的兜底转换器
         * <p>
         * 嵌套 UDF 保留可调用语义，其他脚本值按目标泛型通过 JSON 转换
         */
        private static final BiFunction<Object, Type, Object> CONVERTER = (source, type) -> {
            boolean javaFunction = type == java.util.function.Function.class
                    || type instanceof ParameterizedType parameterizedType
                    && parameterizedType.getRawType() == java.util.function.Function.class;

            if (source instanceof Wrapper wrapper) {
                if (javaFunction) {
                    return (java.util.function.Function<Object[], Object>) wrapper::apply;
                }
                return wrapper.udf;
            }

            if (javaFunction && source instanceof java.util.function.Function) {
                return source;
            }

            if (!(source instanceof String)) {
                source = JsonUtils.toJson(source);
            }
            Object value = JsonUtils.toBean((String) source, new TypeReference<>() {
                @Override
                public Type getType() {
                    return type;
                }
            });
            return value != null ? value : source;
        };

        private final Object udf;

        private final Class<?> type;

        private final Map<String, MethodGroup> methodGroups;

        private final MethodGroup applyGroup;

        private final List<MethodInfo> methodInfos;

        public Wrapper(Object udf) {
            this(udf, udf.getClass(), Function.identity());
        }

        private Wrapper(Object udf, Class<?> type, Function<Method, Method> invocationMethodResolver) {
            this.udf = udf;
            this.type = type;
            Resolution resolution = resolve(type, invocationMethodResolver);
            this.methodGroups = resolution.methodGroups;
            this.applyGroup = methodGroups.get("apply");
            this.methodInfos = resolution.methodInfos;
        }

        @Override
        public Object apply(Object... params) {
            if (applyGroup == null) {
                throw new RuntimeException("udf method is not exposed: apply");
            }
            return applyGroup.invoke(udf, params);
        }

        @Override
        public Object invoke(String methodName, Object... params) {
            MethodGroup methodGroup = methodGroups.get(methodName);
            if (methodGroup == null) {
                throw new RuntimeException("udf method is not exposed: " + methodName);
            }
            return methodGroup.invoke(udf, params);
        }

        @Override
        public boolean contains(String methodName) {
            return methodGroups.containsKey(methodName);
        }

        @Override
        public List<MethodInfo> methods() {
            return methodInfos;
        }

        @Override
        public Class<?> type() {
            return type;
        }

        /**
         * 收集已标注方法并构建方法名与形参个数索引
         * <p>
         * 元数据始终来自 {@code type}，实际调用方法由映射器决定
         *
         * @param type                     元数据解析类型
         * @param invocationMethodResolver 调用方法映射器
         * @return 已建立索引的方法解析结果
         */
        private static Resolution resolve(Class<?> type, Function<Method, Method> invocationMethodResolver) {
            Collection<Method> discovered = Reflections.methods(type, method ->
                    method.isAnnotationPresent(UdfMethod.class)
                            && Modifier.isPublic(method.getModifiers())
                            && !Modifier.isStatic(method.getModifiers())
                            && !method.isBridge()
                            && !method.isSynthetic());
            List<Method> exposed = new ArrayList<>(discovered);
            exposed.sort(Comparator.comparing(Method::getName)
                    .thenComparingInt(Method::getParameterCount)
                    .thenComparing(Method::toGenericString));

            Map<String, List<Signature>> signatures = new LinkedHashMap<>();
            List<MethodInfo> methodInfos = new ArrayList<>(exposed.size());
            for (Method method : exposed) {
                UdfMethod annotation = method.getAnnotation(UdfMethod.class);
                MethodInfo methodInfo = new MethodInfo(method, annotation.description());
                Method invocationMethod = invocationMethodResolver.apply(method);
                signatures.computeIfAbsent(method.getName(), name -> new ArrayList<>())
                        .add(new Signature(invocationMethod, methodInfo));
                methodInfos.add(methodInfo);
            }

            Map<String, MethodGroup> methodGroups = new LinkedHashMap<>();
            for (Map.Entry<String, List<Signature>> entry : signatures.entrySet()) {
                methodGroups.put(entry.getKey(), new MethodGroup(type, entry.getKey(), entry.getValue()));
            }
            return new Resolution(methodGroups, methodInfos);
        }

        private static final class Resolution {

            private final Map<String, MethodGroup> methodGroups;
            private final List<MethodInfo> methodInfos;

            private Resolution(Map<String, MethodGroup> methodGroups, List<MethodInfo> methodInfos) {
                this.methodGroups = methodGroups;
                this.methodInfos = methodInfos;
            }
        }

        /**
         * 同一方法名下的定长和可变参数签名索引
         */
        private static final class MethodGroup {

            private final String name;
            private final Signature[] signatures;
            private final Signature varArgsSignature;

            private MethodGroup(Class<?> type, String name, List<Signature> methods) {
                this.name = name;
                int maxArity = -1;
                int maxFixedArity = -1;
                for (Signature signature : methods) {
                    int arity = signature.parameterTypes.length;
                    maxArity = Math.max(maxArity, arity);
                    if (!signature.methodInfo.isVarArgs()) {
                        maxFixedArity = Math.max(maxFixedArity, arity);
                    }
                }

                // 声明签名数组用于检查同形参个数冲突，定长签名数组用于运行时精确分派
                Signature[] declaredSignatures = new Signature[maxArity + 1];
                Signature[] fixedSignatures = new Signature[maxFixedArity + 1];
                Signature varArgs = null;
                for (Signature signature : methods) {
                    int arity = signature.parameterTypes.length;
                    if (declaredSignatures[arity] != null) {
                        throw new IllegalArgumentException("ambiguous udf method "
                                + type.getName() + "." + name + " with " + arity + " parameters");
                    }
                    declaredSignatures[arity] = signature;
                    if (signature.methodInfo.isVarArgs()) {
                        if (varArgs != null) {
                            throw new IllegalArgumentException("multiple varargs udf methods "
                                    + type.getName() + "." + name);
                        }
                        varArgs = signature;
                    } else {
                        fixedSignatures[arity] = signature;
                    }
                }
                this.signatures = fixedSignatures;
                this.varArgsSignature = varArgs;
            }

            /**
             * 先匹配精确定长签名，未命中时再回退可变参数签名
             * <p>
             * 可变参数尾部会先打包为数组，再统一执行参数类型转换
             *
             * @param target 实际调用对象
             * @param params 调用实参
             * @return UDF 执行结果
             */
            private Object invoke(Object target, Object[] params) {
                Signature signature = params.length < signatures.length ? signatures[params.length] : null;
                if (signature == null
                        && varArgsSignature != null
                        && params.length >= varArgsSignature.parameterTypes.length - 1) {
                    signature = varArgsSignature;
                }
                if (signature == null) {
                    throw new RuntimeException("no exposed udf method " + name
                            + " has " + params.length + " parameters");
                }
                if (signature.methodInfo.isVarArgs()) {
                    int fixedCount = signature.parameterTypes.length - 1;
                    Object[] invocationParams = new Object[signature.parameterTypes.length];
                    System.arraycopy(params, 0, invocationParams, 0, fixedCount);
                    Object[] variableParams = new Object[params.length - fixedCount];
                    System.arraycopy(params, fixedCount, variableParams, 0, variableParams.length);
                    invocationParams[fixedCount] = variableParams;
                    params = invocationParams;
                }
                Object[] casts = Reflections.convert(params, signature.parameterTypes, CONVERTER);
                return Reflections.invoke(signature.invocationMethod, target, casts);
            }
        }

        /**
         * 将元数据签名与实际反射调用方法关联
         */
        private static final class Signature {

            private final Method invocationMethod;
            private final Type[] parameterTypes;
            private final MethodInfo methodInfo;

            private Signature(Method invocationMethod, MethodInfo methodInfo) {
                invocationMethod.setAccessible(true);
                this.invocationMethod = invocationMethod;
                this.parameterTypes = methodInfo.getParameterTypes();
                this.methodInfo = methodInfo;
            }
        }
    }
}
