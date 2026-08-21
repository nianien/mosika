package com.nianien.mosika.eval.parser;


import com.nianien.mosika.eval.node.RuleNode;

import java.util.function.BiFunction;


/**
 * 规则 DSL 解析过程中的命名规则节点生成器
 * <p>
 * 输入是解析器识别出的单个规则 ID，不是完整 DSL 表达式
 * 节点类型和复用策略由创建该生成器的 {@link NodeBuilder} 决定
 * <p>
 * Created on 2022/6/17
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public interface NodeGenerator extends BiFunction<String, String, RuleNode> {

    /**
     * 生成指定规则 ID 对应的规则节点
     *
     * @param ruleId 规则 ID
     * @return 命名规则节点
     */
    @Override
    RuleNode apply(String ruleId, String arguments);
}
