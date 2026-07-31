package com.skyfalling.mousika.suite;

import com.skyfalling.mousika.eval.parser.NodeBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 规则流定义
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@AllArgsConstructor
public class RuleFlowDefinition {

    /**
     * 规则流ID
     */
    private String id;

    /**
     * 规则流DSL
     */
    private String dsl;

    /**
     * 将规则流定义编译为可执行规则流
     *
     * @return 可执行规则流
     */
    public RuleFlow compile() {
        return new RuleFlow(id, NodeBuilder.build(dsl));
    }
}
