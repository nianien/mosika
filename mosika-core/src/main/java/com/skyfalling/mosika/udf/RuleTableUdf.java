package com.skyfalling.mosika.udf;

import com.skyfalling.mosika.utils.JsonUtils;

import java.util.Collections;
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
        RuleTableUdf ruleTable = JsonUtils.toBean(json, RuleTableUdf.class);
        if (ruleTable == null) {
            throw new IllegalArgumentException("decision table cannot be null");
        }
        ruleTable.validate();
        return ruleTable;
    }


    /**
     * 根据对象属性获取匹配结果
     */
    public Map<String, String> apply(Object target) {
        String[] rowKeys = new String[keys.length];
        Map<String, Object> map;
        if (target == null) {
            map = Collections.emptyMap();
        } else {
            String json = JsonUtils.toJson(target);
            map = JsonUtils.toMap(json, String.class, Object.class);
            if (map == null) {
                map = Collections.emptyMap();
            }
        }
        for (int i = 0; i < keys.length; i++) {
            Object value = map.get(keys[i]);
            rowKeys[i] = value == null ? "" : value.toString();
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
                if (datum[i] != null && !datum[i].isEmpty() && !Objects.equals(rowKeys[i], datum[i])) {
                    continue out;
                }
            }
            Map<String, String> valueData = new LinkedHashMap<>();
            for (int j = 0; j < values.length; j++) {
                valueData.put(values[j], datum[j + rowKeys.length]);
            }
            return valueData;
        }
        return null;
    }

    private void validate() {
        if (keys == null) {
            throw new IllegalArgumentException("decision table keys cannot be null");
        }
        if (values == null) {
            throw new IllegalArgumentException("decision table values cannot be null");
        }
        if (data == null) {
            throw new IllegalArgumentException("decision table data cannot be null");
        }
        int expectedColumns = keys.length + values.length;
        for (int i = 0; i < data.length; i++) {
            String[] row = data[i];
            if (row == null || row.length != expectedColumns) {
                throw new IllegalArgumentException("decision table row " + i + " must contain "
                        + expectedColumns + " columns");
            }
        }
    }

}
