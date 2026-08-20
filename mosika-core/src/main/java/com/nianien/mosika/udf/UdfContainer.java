package com.nianien.mosika.udf;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * UDF 容器:保存 UDF 定义树并通过 {@link UdfCompiler} 编译为引擎对象图
 * <p>
 * 分组创建、叶子编译和成员绑定都由引擎适配层负责
 * 本类只掌握定义树结构,不包含任何脚本引擎类型
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
     * UDF 对象图编译回调
     * JS/Java 的区分和分组表示都由实现决定
     */
    public interface UdfCompiler {
        Object group(String name);

        Object compile(UdfDefinition definition);

        void bind(Object group, String name, Object member);

        void complete(Object group);
    }

    /**
     * 编译 UDF 定义树
     *
     * @param compiler 引擎对象图编译回调
     * @return 顶层命名空间:name → 已编译分组或叶子
     */
    public Map<String, Object> compile(UdfCompiler compiler) {
        Map<String, Object> result = new LinkedHashMap<>();
        udfDefined.forEach((name, node) ->
                result.put(name, compileNode(name, node, compiler)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object compileNode(String name, Object node, UdfCompiler compiler) {
        if (node instanceof UdfDefinition definition) {
            return compiler.compile(definition);
        }
        Object group = compiler.group(name);
        ((Map<String, Object>) node).forEach((key, value) ->
                compiler.bind(group, key, compileNode(key, value, compiler)));
        compiler.complete(group);
        return group;
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
