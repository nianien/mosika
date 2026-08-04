package com.skyfalling.mosika.suite;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 规则流定义
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
@AllArgsConstructor
public class RuleFlowDefinition {

    /**
     * 规则流ID
     */
    private final String id;

    /**
     * 规则流DSL
     */
    private final String dsl;
}
