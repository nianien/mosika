package com.skyfalling.mosika.eval.node;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 任意子规则命中
 * <p>
 * 继承 {@link OrNode} 复用有序短路实现，同时保留 {@code any(...)} 的语法身份
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class AnyNode extends OrNode {

    /**
     * 创建任意命中节点
     *
     * @param nodes 子规则
     */
    public AnyNode(RuleNode... nodes) {
        super(nodes);
    }

    @Override
    public String expr() {
        return "any(" + getNodes().stream()
                .map(Objects::toString)
                .collect(Collectors.joining(",")) + ")";
    }

    @Override
    public String toString() {
        return expr();
    }
}
