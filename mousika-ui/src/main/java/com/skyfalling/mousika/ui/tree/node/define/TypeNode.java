package com.skyfalling.mousika.ui.tree.node.define;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.skyfalling.mousika.ui.tree.resolver.NodeTypeResolver;

/**
 * 节点类型定义
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@JsonTypeInfo(use = Id.CUSTOM, property = "type")/*用于JSON序列化的多态支持*/
@JsonTypeIdResolver(NodeTypeResolver.class)
public interface TypeNode {

    /**
     * 原始表达式
     */
    String getExpr();

}