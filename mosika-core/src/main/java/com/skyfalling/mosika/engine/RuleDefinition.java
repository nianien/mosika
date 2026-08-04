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
     * 使用方式
     */
    private int useType = 0;

}
