package com.skyfalling.mousika.ui.tree.node.flow;


import com.skyfalling.mousika.ui.tree.node.rule.RNode;
import lombok.Getter;
import lombok.Setter;

/**
 * 规则判断流程节点，是流程递归域嵌入纯规则递归域的边界。
 * <p>
 * {@code rule}只能引用由{@code RNode}/{@code LNode}/{@code HNode}组成的纯规则树；
 * 命中后的流程保存在继承自{@link CNode}的{@code action}中。规则树不能反向引用流程节点。
 * <p>
 * 节点自身继承的{@code label}是通用展示标签；规则在UI中显示的可编辑名称来自
 * {@code rule.name}，不保存在外层{@code JNode}上。
 * <pre>
 *     jN
 *    / |
 *   /  |
 *  rN  fN
 * </pre>
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
@Setter
public class JNode extends CNode {

    /**
     * 判断节点引用的纯规则树。
     * <p>
     * 默认使用恒真原子规则，保证新建节点在配置具体规则前结构完整。
     */
    private RNode rule = new RNode("true");

    /**
     * 创建判断节点，使用{@code J}作为稳定结构标识。
     */
    public JNode() {
        super("J");
    }

    /**
     * 生成规则侧DSL，不包含命中后的{@code action}流程。
     *
     * @return 规则树DSL表达式
     */
    @Override
    public String ruleExpr() {
        return (isNegative() ? "!" : "") + rule.ruleExpr();
    }
}
