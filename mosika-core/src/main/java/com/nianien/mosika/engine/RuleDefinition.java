package com.nianien.mosika.engine;

import lombok.*;

/**
 * 按规则 ID 注册到规则套件的规则定义
 * <p>
 * 原子规则的表达式是 JavaScript 脚本
 * 复合规则的表达式是由规则 ID 组成的规则 DSL
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor
public class RuleDefinition {

    /**
     * JavaScript 原子规则类型
     */
    public static final int RULE_TYPE_ATOMIC = 0;

    /**
     * 规则 ID DSL 复合规则类型
     */
    public static final int RULE_TYPE_COMPOSITE = 2;

    /**
     * 规则 ID
     */
    @NonNull
    private String ruleId;
    /**
     * 规则表达式，格式由 {@link #ruleType} 决定
     */
    @NonNull
    private String expression;
    /**
     * 规则描述模板，支持 JavaScript 表达式插值
     */
    @NonNull
    private String desc;

    /**
     * 规则类型，支持 {@link #RULE_TYPE_ATOMIC} 和 {@link #RULE_TYPE_COMPOSITE}
     */
    private int ruleType = RULE_TYPE_ATOMIC;

}
