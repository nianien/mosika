package com.nianien.mosika.udf;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * UDF 容器:把 UDF 定义编译为 Java 对象图。
 * <p>
 * 分组由 ByteBuddy 生成带 public 字段的 Java 对象;<b>叶子由调用方通过 {@link UdfCompiler} 回调编译</b>
 * ——JS 源码/Java UDF 被脚本引擎编译成可调用值,直接设入分组字段。因此本类掌握树结构与分组生成,
 * 叶子如何变为脚本可调用值完全由引擎适配层决定,本类不含任何脚本引擎类型。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * @since 2023/11/7
 * Copyright (c) 2004-2029 All Rights Reserved.
 **/
public class UdfContainer {

    /**
     * 注册阶段的嵌套树:分组为嵌套 {@link Map},叶子为 {@link UdfDefinition} 原始定义;
     * JS/Java 的区分推迟到 {@link #compile} 时由 {@link UdfCompiler} 判断
     */
    private final Map<String, Object> udfDefined = new ConcurrentHashMap<>();

    public UdfContainer(List<UdfDefinition> udfDefinitions) {
        udfDefinitions.forEach(this::register);
    }

    /**
     * 叶子编译回调:把单个 UDF 叶子({@link UdfDefinition})编译成脚本引擎可调用值。
     * JS/Java 的区分由实现根据 {@link UdfDefinition#getUdf()} 自行判断;分组由 {@link UdfContainer}
     * 用 ByteBuddy 组装。
     */
    @FunctionalInterface
    public interface UdfCompiler {
        Object compile(UdfDefinition definition);
    }

    /**
     * 编译 UDF 树:分组生成 ByteBuddy Java 对象,叶子经 {@code compiler} 回调编译并设入字段。
     *
     * @param compiler 叶子编译回调(引擎相关)
     * @return 顶层命名空间:name → 分组 Java 对象 | 已编译叶子
     */
    public Map<String, Object> compile(UdfCompiler compiler) {
        Map<String, Object> result = new LinkedHashMap<>();
        udfDefined.forEach((name, node) ->
                result.put(name, compileNode("UdfGroup$" + name, node, compiler)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object compileNode(String className, Object node, UdfCompiler compiler) {
        if (node instanceof UdfDefinition definition) {
            return compiler.compile(definition);
        }
        Map<String, Object> group = (Map<String, Object>) node;
        try {
            DynamicType.Builder<Object> builder = new ByteBuddy()
                    .subclass(Object.class)
                    .name(className);
            for (String field : group.keySet()) {
                builder = builder.defineField(field, Object.class, Visibility.PUBLIC);
            }
            Class<?> type = builder.make()
                    .load(Thread.currentThread().getContextClassLoader())
                    .getLoaded();
            Object instance = type.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> entry : group.entrySet()) {
                String field = entry.getKey();
                type.getField(field).set(instance,
                        compileNode(className + "$" + field, entry.getValue(), compiler));
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to compile udf group: " + className, e);
        }
    }

    @SuppressWarnings("unchecked")
    public void register(UdfDefinition udfDefinition) {
        String group = udfDefinition.getGroup();
        String name = udfDefinition.getName();
        Map<String, Object> map = udfDefined;
        if (group != null && !group.isEmpty()) {
            String[] tokens = group.replaceAll("\\s", "").split("\\.+");
            int i = 0;
            for (; i < tokens.length; i++) {
                String token = tokens[i];
                Object value = map.computeIfAbsent(token, key -> new LinkedHashMap<String, Object>());
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
        // 叶子:存原始定义,编译推迟到 compile() 阶段
        map.put(name, udfDefinition);
    }
}
