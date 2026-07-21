package com.skyfalling.mousika.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created on 2022/6/29
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class JsonUtils {

    @Getter
    private static ObjectMapper objectMapper;

    static {
        objectMapper = JsonMapper.builder()
                // 允许字段名不用引号
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                // 允许使用单引号
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                // 允许数字含有前导0
                .enable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                // 允许未知的属性
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                //空字段不序列化
                .defaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_NULL, Include.NON_NULL))
                .visibility(PropertyAccessor.FIELD, Visibility.ANY)
                //无需默认构造方法
                .addModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
                .addModule(new KotlinModule.Builder().build())
                .addModule(new ProtobufModule())
                .build();
    }


    /**
     * json转T对象
     * <pre>
     *     String json="{\"key\":[1,2,3]}";
     *     TypeReference ref = new TypeReference&lt;Map&lt;String,String[]&gt;&gt;() {};
     *     Map&lt;String,String[]&gt; map=toBean(json,ref)
     * </pre>
     */
    public static <T> T toBean(String json, TypeReference<T> typeReference) {
        return readValue(json, objectMapper.getTypeFactory().constructType(typeReference));
    }

    /**
     * json转Object对象, 根据json字符串的结构自动调整为对应的数据类型, 具体对应关系如下：<br>
     * 1)字符串-&gt;String类型<br>
     * 2)整数-&gt;int类型<br>
     * 3)长整数-&gt;long类型<br>
     * 4)实数-&gt;double类型 <br>
     * 5)键值对-&gt;(LinkedHash)Map类型<br>
     * 6)数组-&gt;(Array)List类型<br>
     */
    public static Object toObject(String json) {
        return toBean(json, Object.class);
    }

    /**
     * json转T对象
     */
    @SneakyThrows
    public static <T> T toBean(String json, Class<T> beanType) {
        return readValue(json, objectMapper.getTypeFactory().constructType(beanType));
    }

    /**
     * json转T对象数组
     */
    @SneakyThrows
    public static <T> T[] toArray(String json, Class<T> elementType) {
        return readValue(json,
                objectMapper.getTypeFactory()
                        .constructArrayType(elementType));
    }

    /**
     * json转List&lt;T&gt;对象
     */
    @SneakyThrows
    public static <T> List<T> toList(String json, Class<T> elementType) {
        return readValue(json,
                objectMapper.getTypeFactory()
                        .constructCollectionType(ArrayList.class, elementType));
    }

    /**
     * json转Map&lt;K,V&gt;对象
     */
    @SneakyThrows
    public static <K, V> Map<K, V> toMap(String json, Class<K> keyType, Class<V> valueType) {
        return readValue(json,
                objectMapper.getTypeFactory()
                        .constructMapType(LinkedHashMap.class, keyType, valueType));
    }

    /**
     * 对象类型转JSON字符串
     */
    @SneakyThrows
    public static String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * 反序列化
     */
    @SneakyThrows
    private static <T> T readValue(String json, JavaType valueType) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return objectMapper.readValue(json, valueType);
    }
}
