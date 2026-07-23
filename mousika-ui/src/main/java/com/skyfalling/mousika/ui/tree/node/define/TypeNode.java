package com.skyfalling.mousika.ui.tree.node.define;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.skyfalling.mousika.ui.tree.resolver.NodeTypeResolver;

/**
 * UI树节点的多态类型契约。
 * <p>
 * JSON中的{@code type}字段由{@link NodeTypeResolver}根据具体节点类型生成并解析，
 * {@code expr}则保留节点自身的表达式或结构标识。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@JsonTypeInfo(use = Id.CUSTOM, property = "type")/*用于JSON序列化的多态支持*/
@JsonTypeIdResolver(NodeTypeResolver.class)
public interface TypeNode {

    /**
     * 返回节点表达式。
     * <p>
     * 原子节点返回规则或动作表达式，结构节点返回稳定的结构标识。
     *
     * @return 节点表达式或结构标识
     */
    String getExpr();

}
