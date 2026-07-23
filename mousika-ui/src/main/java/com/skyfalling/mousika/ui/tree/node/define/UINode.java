package com.skyfalling.mousika.ui.tree.node.define;

import lombok.Getter;

/**
 * UI树节点的抽象基类。
 * <p>
 * 该类只保存所有UI节点共有的表达式和通用展示标签，不定义节点之间的递归关系。
 * 流程后继、流程分支和规则子树分别由具体节点类型维护。
 * <p>
 * {@code label}用于描述节点在通用UI中的展示标签，默认取具体节点的类名，
 * 不参与规则求值和流程执行；规则节点的{@code name}则是规则自身可编辑的名称或别名，
 * 两者语义不同，不能互相替代。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Getter
public abstract class UINode implements TypeNode {

    /**
     * 节点表达式。
     * <p>
     * 原子节点中保存规则或动作表达式；结构节点中保存用于稳定识别该结构的类型标识。
     * 具体求值和转换语义由节点子类及{@code UINodeAdapter}解释。
     */
    private final String expr;

    /**
     * 通用展示标签。
     * <p>
     * 该字段属于所有UI节点，通常用于显示节点类别，不表示规则名称；
     * 规则名称由规则节点单独维护。
     */
    private final String label;

    /**
     * 使用节点类名作为默认展示标签。
     *
     * @param expr 节点表达式或结构类型标识
     */
    public UINode(String expr) {
        this.expr = expr;
        this.label = this.getClass().getSimpleName();
    }

    /**
     * 使用指定的展示标签创建节点。
     *
     * @param expr  节点表达式或结构类型标识
     * @param label 节点展示标签
     */
    public UINode(String expr, String label) {
        this.expr = expr;
        this.label = label;
    }
}
