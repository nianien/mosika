package com.skyfalling.mosika.mock;

import com.skyfalling.mosika.engine.RuleDefinition;
import com.skyfalling.mosika.engine.UdfDefinition;
import com.skyfalling.mosika.suite.RuleLoader;

import java.util.List;

/**
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class SimpleRuleLoader implements RuleLoader {
    private List<RuleDefinition> rules;
    private List<UdfDefinition> udfs;

    public SimpleRuleLoader(List<RuleDefinition> rules, List<UdfDefinition> udfs) {
        this.rules = rules;
        this.udfs = udfs;
    }

    @Override
    public List<RuleDefinition> loadRules() {
        return rules;
    }

    @Override
    public List<UdfDefinition> loadUdfs() {
        return udfs;
    }
}
