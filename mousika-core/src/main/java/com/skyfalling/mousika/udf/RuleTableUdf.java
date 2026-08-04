package com.skyfalling.mousika.udf;

import com.skyfalling.mousika.utils.JsonUtils;
import lombok.SneakyThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 基于决策表的UDF定义
 * Created on 2022/6/30
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class RuleTableUdf implements Function<Object, Map<String, String>> {
    private String[] keys;
    private String[] values;
    private String[][] data;

    private RuleTableUdf() {
        this.keys = new String[0];
        this.values = new String[0];
        this.data = new String[0][];
    }


    /**
     * 根据json反序列化
     *
     * @param json
     * @return
     */
    public static RuleTableUdf fromJson(String json) {
        return JsonUtils.toBean(json, RuleTableUdf.class);
    }


    /**
     * 根据对象属性获取匹配结果
     */
    @SneakyThrows
    public Map<String, String> apply(Object target) {
        String[] rowKeys = new String[keys.length];
        String json = JsonUtils.toJson(target);
        Map map = JsonUtils.toMap(json, String.class, Object.class);
        for (int i = 0; i < keys.length; i++) {
            rowKeys[i] = map.getOrDefault(keys[i], "").toString();
        }
        return getData(rowKeys);
    }


    /**
     * 根据条件字段获取匹配结果
     *
     * @param rowKeys 条件字段值列表
     */
    private Map<String, String> getData(String[] rowKeys) {
        out:
        for (String[] datum : data) {
            for (int i = 0; i < rowKeys.length; i++) {
                if (!"".equals(datum[i]) && !Objects.equals(rowKeys[i], datum[i])) {
                    continue out;
                }
            }
            Map<String, String> valueData = new LinkedHashMap();
            for (int j = 0; j < values.length; j++) {
                valueData.put(values[j], datum[j + rowKeys.length]);
            }
            return valueData;
        }
        return null;
    }

}
