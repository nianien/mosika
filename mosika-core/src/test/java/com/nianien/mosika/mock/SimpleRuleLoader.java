package com.nianien.mosika.mock;

import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.engine.UdfDefinition;
import com.nianien.mosika.suite.RuleLoader;

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
