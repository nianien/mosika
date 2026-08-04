package com.skyfalling.mousika.utils;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GraalJS运行时。所有上下文共享同一个编译引擎，但不共享可变的JS状态。
 */
public final class JsRuntime {

    public static final String LANGUAGE_ID = "js";

    private static final Engine ENGINE = Engine.create();

    private JsRuntime() {
    }

    /**
     * 创建隔离的JS上下文。保留原有Nashorn兼容和Java互操作能力。
     */
    public static Context createContext() {
        return Context.newBuilder(LANGUAGE_ID)
                .engine(ENGINE)
                .allowExperimentalOptions(true)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .build();
    }

    /**
     * 创建可被共享引擎缓存的脚本源。
     */
    public static Source createSource(String code, String name) {
        return Source.newBuilder(LANGUAGE_ID, code, name)
                .cached(true)
                .buildLiteral();
    }

    /**
     * 将绑定到Polyglot Context的结果转换为独立Java值。
     */
    public static Object toJava(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.hasArrayElements()) {
            int size = Math.toIntExact(value.getArraySize());
            List<Object> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                result.add(toJava(value.getArrayElement(i)));
            }
            return result;
        }
        if (value.hasMembers()) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                result.put(key, toJava(value.getMember(key)));
            }
            return result;
        }
        return value.as(Object.class);
    }
}
