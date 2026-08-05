package com.skyfalling.mosika.engine;

import lombok.*;

/**
 * 规则定义
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
public class RuleDefinition {

    public static final int USE_TYPE_ATOMIC = 0;
    public static final int USE_TYPE_COMPOSITE = 2;

    /**
     * 规则id
     */
    @NonNull
    private String ruleId;
    /**
     * 规则表达式
     */
    @NonNull
    private String expression;
    /**
     * 规则描述
     */
    @NonNull
    private String desc;

    /**
     * 规则定义类型：0 表示 JavaScript 原子规则，2 表示规则 ID DSL 复合规则。
     */
    private int useType = USE_TYPE_ATOMIC;

}
