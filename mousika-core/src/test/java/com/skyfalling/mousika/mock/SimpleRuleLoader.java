package com.skyfalling.mousika.mock;

import com.skyfalling.mousika.engine.RuleDefinition;
import com.skyfalling.mousika.engine.UdfDefinition;
import com.skyfalling.mousika.suite.RuleFlowDefinition;
import com.skyfalling.mousika.suite.RuleLoader;
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
