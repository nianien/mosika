package com.skyfalling.mosika.ui.tree.node.define;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.skyfalling.mosika.ui.tree.resolver.NodeTypeResolver;

/**
 * UITree 节点的 JSON 多态标记
 * <p>
 * 该接口不保存字段也不定义业务行为
 * JSON 中的 {@code type} 字段由 {@link NodeTypeResolver} 根据节点的实际类型生成和解析
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@JsonTypeInfo(use = Id.CUSTOM, property = "type")/*用于JSON序列化的多态支持*/
@JsonTypeIdResolver(NodeTypeResolver.class)
public interface TypeNode {


}
