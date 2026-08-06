package com.skyfalling.mosika.engine;

import lombok.*;

/**
 * 注册到规则引擎的 UDF 定义
 * <p>
 * UDF 对象支持 Java 对象或 JavaScript 源码字符串
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
public class UdfDefinition {

    /**
     * 点分隔的 UDF 分组，可为空
     */
    private String group;
    /**
     * UDF 名称
     */
    @NonNull
    private String name;
    /**
     * Java UDF 对象或 JavaScript 源码字符串
     */
    @NonNull
    private Object udf;

}
