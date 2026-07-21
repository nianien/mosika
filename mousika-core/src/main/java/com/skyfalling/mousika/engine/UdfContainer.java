package com.skyfalling.mousika.engine;

import com.cudrania.core.utils.StringUtils;
import com.skyfalling.mousika.udf.JsUdf;
import com.skyfalling.mousika.udf.UdfDelegate;
import lombok.SneakyThrows;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType.Builder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * UDF容器，用于将UDF分组编译成对象
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * @since 2023/11/7
 * Copyright (c) 2004-2029 All Rights Reserved.
 **/
public class UdfContainer {
    /**
     * 注册的UDF
     */
    private Map<String, Object> udfDefined = new ConcurrentHashMap<>();

    /**
     * UDF列表
     *
     * @param udfDefinitions
     */
    public UdfContainer(List<UdfDefinition> udfDefinitions) {
        udfDefinitions.forEach(udf -> register(udf));
    }

    /**
     * 分组的UDF集成一个对象
     *
     * @return
     */
    public Map<String, Object> compile() {
        Map<String, Object> udfCompiled = new ConcurrentHashMap<>();
        //编译udf
        this.udfDefined.forEach((k, v) -> udfCompiled.put(k, compileUdf("UDF$" + StringUtils.capitalize(k), v)));
        return udfCompiled;
    }


    /**
     * 注册自定义函数
     *
     * @param udfDefinition
     */
    public void register(UdfDefinition udfDefinition) {
        register(udfDefinition.getGroup(), udfDefinition.getName(), udfDefinition.getUdf());
    }

    /**
     * 注册自定义函数
     *
     * @param group
     * @param name
     * @param udf
     */
    private void register(String group, String name, Object udf) {
        Map map = this.udfDefined;
        if (StringUtils.isNotEmpty(group)) {
            String[] tokens = group.replaceAll("\\s", "").split("\\.+");
            int i = 0;
            for (; i < tokens.length; i++) {
                String token = tokens[i];
                Object o = map.computeIfAbsent(token, k -> new HashMap<>());
                if (o instanceof Map) {
                    map = (Map) o;
                } else {
                    String conflictName = Arrays.stream(tokens).limit(i + 1).collect(Collectors.joining("."));
                    throw new IllegalArgumentException("udf: " + conflictName + " is already defined!");
                }
            }
        }
        if (map.containsKey(name)) {
            throw new IllegalArgumentException("udf: " + name + " is already defined!");
        }
        doRegister(map, name, udf);
    }

    /**
     * 注册自定义函数
     *
     * @param map
     * @param name
     * @param udf
     */
    private void doRegister(Map map, String name, Object udf) {
        if (udf instanceof String) {
            udf = new JsUdf(name, (String) udf);
        }
        //代理JsUdf
        map.put(name, UdfDelegate.of(udf));
    }


    /**
     * 将udf映射表编译成类对象<p/>
     *
     * @param name
     * @param udf
     * @return
     */
    @SneakyThrows
    private static Object compileUdf(String name, Object udf) {
        if (!(udf instanceof Map)) {
            return udf;
        }
        Map<String, Object> udfMap = (Map<String, Object>) udf;
        Builder<Object> subclass = new ByteBuddy()
                .subclass(Object.class)
                .name(name);
        for (Entry<String, Object> entry : udfMap.entrySet()) {
            subclass = subclass.defineField(entry.getKey(), Object.class, Visibility.PUBLIC);
        }
        Object instance = subclass
                .make()
                .load(Thread.currentThread().getContextClassLoader())
                .getLoaded().getDeclaredConstructor().newInstance();
        for (Entry<String, Object> entry : udfMap.entrySet()) {
            //udf的名称,不含命名空间
            String key = entry.getKey();
            //udf的实例
            Object value = entry.getValue();
            String clazz = name + "$" + StringUtils.capitalize(key);
            //注意: 这里不是setter方法,而是setField的底层实现,直接访问字段
            MethodHandle setter = MethodHandles.lookup().findSetter(instance.getClass(), key, Object.class);
            setter.bindTo(instance).invoke(compileUdf(clazz, value));
        }
        return instance;
    }
}
