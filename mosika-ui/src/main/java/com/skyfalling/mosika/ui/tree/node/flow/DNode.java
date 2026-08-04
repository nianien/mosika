package com.skyfalling.mosika.ui.tree.node.flow;

import com.skyfalling.mosika.ui.tree.node.define.BranchNode;
import com.skyfalling.mosika.ui.tree.node.define.FlowNode;
import lombok.Getter;
import lombok.Setter;

/**
 * 有序互斥决策流程节点。
 * <p>
 * {@code branches}按顺序保存{@link CNode}或其子类条件分支。执行时依次判断，
 * 命中首个分支后执行该分支的{@code action}并停止继续判断；
 * 继承的{@code action}字段表示所有条件均未命中时执行的可选默认流程。
 * <pre>
 *      dN
 *     / | \
 *    /  |  \
 *   cN  cN  fN
 * </pre>
 * @author skyfalling {@literal <skyfalling@live.com>}
 * Created on 2022-07-19
 */
@Getter
@Setter
public class DNode extends BranchNode<CNode> {

    /**
     * 所有条件分支均未命中时执行的可选默认流程。
     */
    private FlowNode action;

    /**
     * 创建决策节点，使用{@code D}作为稳定结构标识。
     */
    public DNode() {
        super("D");
    }
}
