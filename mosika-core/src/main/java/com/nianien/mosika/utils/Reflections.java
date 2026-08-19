package com.nianien.mosika.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * 反射工具:方法收集、参数类型转换、方法调用。
 * <p>
 * 不含任何业务语义,类型转换的兜底策略由调用方通过 {@code converter} 注入。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public final class Reflections {

    private Reflections() {
    }

    /**
     * 收集类及其父类型声明的、满足 {@code filter} 的非重复方法。
     * <p>
     * 合并 {@link Class#getMethods()}(含继承的 public 方法)与
     * {@link Class#getDeclaredMethods()}(本类声明的方法),去重后过滤。
     *
     * @param type   目标类
     * @param filter 方法筛选条件
     * @return 满足条件的方法集合
     */
    public static Collection<Method> methods(Class<?> type, Predicate<Method> filter) {
        Set<Method> methods = new LinkedHashSet<>();
        for (Method method : type.getMethods()) {
            methods.add(method);
        }
        for (Method method : type.getDeclaredMethods()) {
            methods.add(method);
        }
        methods.removeIf(filter.negate());
        return methods;
    }

    /**
     * 按目标参数类型逐个转换实参。
     * <p>
     * 要求 {@code parameters} 与 {@code parameterTypes} 长度一致,否则抛出 {@link IllegalArgumentException}。
     *
     * @param parameters     待转换的实参
     * @param parameterTypes 目标参数类型
     * @param converter      基础转换未命中时的兜底转换
     * @return 转换后的实参数组
     * @throws IllegalArgumentException 实参个数与目标类型个数不一致
     */
    public static Object[] convert(Object[] parameters, Type[] parameterTypes,
                                   BiFunction<Object, Type, Object> converter) {
        if (parameters.length != parameterTypes.length) {
            throw new IllegalArgumentException("parameter count " + parameters.length
                    + " does not match target type count " + parameterTypes.length);
        }
        Object[] result = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            result[i] = convert(parameters[i], parameterTypes[i], converter);
        }
        return result;
    }

    /**
     * 把单个实参转换为目标类型。
     * <p>
     * {@code null} 直接返回;目标是 {@link Class} 且实参已是其实例时原样返回(该分支先于
     * {@code converter});字符串到基础类型和枚举按名称解析;其余情况交给 {@code converter}。
     *
     * @param value     原始实参
     * @param type      目标参数类型
     * @param converter 兜底转换
     * @return 转换后的值
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object convert(Object value, Type type, BiFunction<Object, Type, Object> converter) {
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
     * 反射调用方法。
     * <p>
     * 解包 {@link InvocationTargetException},向上抛出被调方法实际抛出的异常;反射本身的
     * {@link IllegalAccessException} 作为基础设施错误包装为 {@link IllegalStateException}。
     *
     * @param method    目标方法
     * @param target    目标实例
     * @param arguments 已转换的实参
     * @return 方法返回值
     */
    public static Object invoke(Method method, Object target, Object[] arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
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
}
