package com.skyfalling.mosika.eval.node;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全部子规则命中
 * <p>
 * 继承 {@link AndNode} 复用有序短路实现，同时保留 {@code all(...)} 的语法身份
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class AllNode extends AndNode {

    /**
     * 创建全部命中节点
     *
     * @param nodes 子规则
     */
    public AllNode(RuleNode... nodes) {
        super(nodes);
    }

    @Override
    public String expr() {
        return "all(" + getNodes().stream()
                .map(Objects::toString)
                .collect(Collectors.joining(",")) + ")";
    }

    @Override
    public String toString() {
        return expr();
    }
}
