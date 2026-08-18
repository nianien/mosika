package com.nianien.mosika.udf;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * UDF 容器:把 UDF 定义构建为引擎无关的命名空间树。
 * <p>
 * 树的叶子:JS UDF 为源码字符串,Java UDF 为 {@link UdfDelegate};分组为嵌套 {@link Map}。
 * 树本身不含任何脚本引擎类型;绑定到具体引擎由引擎适配层完成
 * (如 {@code com.nianien.mosika.engine.rhino.RhinoUdfBinder})。
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

    /**
     * 引擎无关的 UDF 命名空间树:name → JS 源码字符串 | {@link UdfDelegate} | 子分组 {@link Map}。
     *
     * @return UDF 命名空间树
     */
    public Map<String, Object> tree() {
        return udfDefined;
    }

    public void register(UdfDefinition udfDefinition) {
        register(udfDefinition.getGroup(), udfDefinition.getName(), udfDefinition.getUdf());
    }

    @SuppressWarnings("unchecked")
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
        // JS UDF 保留源码字符串(由引擎适配层编译),Java UDF 预解析为调用代理
        map.put(name, udf instanceof String source ? source : UdfDelegate.of(udf));
    }
}
