package com.nianien.mosika.ui.tree.node.define;

import lombok.Data;

/**
 * 可生成规则 DSL 的节点基类
 * <p>
 * 规则节点只描述规则表达式，不引用执行拓扑中的 {@link UINode}
 * Created on 2023/5/2
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
public abstract class RuleNode extends NameNode implements TypeNode {
    /**
     * 生成当前规则节点对应的 DSL 表达式
     *
     * @return 规则DSL表达式
     */
    public abstract String ruleExpr();
}
