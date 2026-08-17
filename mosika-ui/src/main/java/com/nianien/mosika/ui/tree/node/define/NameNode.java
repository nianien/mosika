package com.nianien.mosika.ui.tree.node.define;

import lombok.Data;

/**
 * 为 UITree 节点提供统一名称的公共基类
 * <p>
 * 名称只用于展示和编辑，不参与执行树编译和规则求值
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
public abstract class NameNode implements TypeNode {

    /** 节点名称 */
    private String name = "";


}
