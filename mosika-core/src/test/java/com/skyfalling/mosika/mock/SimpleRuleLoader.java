package com.skyfalling.mosika.mock;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.engine.UdfDefinition;
import com.skyfalling.mosika.suite.RuleFlowDefinition;
import com.skyfalling.mosika.suite.RuleLoader;
import lombok.AllArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@AllArgsConstructor
public class SimpleRuleLoader implements RuleLoader {
    private List<RuleDefinition> rules;
    private List<UdfDefinition> udfs;
    private List<RuleFlowDefinition> flowDefinitions;

    public SimpleRuleLoader(List<RuleDefinition> rules, List<UdfDefinition> udfs) {
        this(rules, udfs, Collections.emptyList());
    }

    @Override
    public List<RuleDefinition> loadRules() {
        return rules;
    }

    @Override
    public List<UdfDefinition> loadUdfs() {
        return udfs;
    }

    @Override
    public List<RuleFlowDefinition> loadFlows() {
        return flowDefinitions;
    }
}
