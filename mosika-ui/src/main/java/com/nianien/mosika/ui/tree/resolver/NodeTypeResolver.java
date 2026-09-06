package com.nianien.mosika.ui.tree.resolver;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.nianien.mosika.ui.tree.node.TreeNode;
import com.nianien.mosika.ui.tree.node.flow.*;
import com.nianien.mosika.ui.tree.node.rule.AtomicNode;
import com.nianien.mosika.ui.tree.node.rule.BooleanNode;
import com.nianien.mosika.ui.tree.node.rule.LogicNode;
import com.nianien.mosika.ui.tree.node.rule.MatchNode;
import lombok.SneakyThrows;

/**
 * 负责 UITree 节点类型标识的序列化和反序列化
 * <p>
 * 节点类型标识使用节点类的简单类名
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class NodeTypeResolver extends TypeIdResolverBase {

    /**
     * 根据节点实例生成类型标识
     *
     * @param value 节点实例
     * @return 节点类型标识
     */
    @Override
    public String idFromValue(Object value) {
        return value.getClass().getSimpleName();
    }

    /**
     * 根据节点实例和建议类型生成类型标识
     *
     * @param value 节点实例
     * @param suggestedType 建议类型
     * @return 节点类型标识
     */
    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return idFromValue(value);
    }

    /**
     * 返回当前解析器使用的类型标识机制
     *
     * @return 自定义类型标识机制
     */
    @Override
    public Id getMechanism() {
        return Id.CUSTOM;
    }


    /**
     * 根据类型标识解析节点类型
     *
     * @param context Jackson 数据绑定上下文
     * @param id 节点类型标识
     * @return 对应的节点类型
     */
    @SneakyThrows
    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        switch (id) {
            case "ActionNode":
                return SimpleType.constructUnsafe(ActionNode.class);
            case "BooleanNode":
                return SimpleType.constructUnsafe(BooleanNode.class);
            case "ConditionNode":
                return SimpleType.constructUnsafe(ConditionNode.class);
            case "DecisionNode":
                return SimpleType.constructUnsafe(DecisionNode.class);
            case "MatchNode":
                return SimpleType.constructUnsafe(MatchNode.class);
            case "LogicNode":
                return SimpleType.constructUnsafe(LogicNode.class);
            case "LoopNode":
                return SimpleType.constructUnsafe(LoopNode.class);
            case "ParallelNode":
                return SimpleType.constructUnsafe(ParallelNode.class);
            case "AtomicNode":
                return SimpleType.constructUnsafe(AtomicNode.class);
            case "SerialNode":
                return SimpleType.constructUnsafe(SerialNode.class);
            case "TreeNode":
                return SimpleType.constructUnsafe(TreeNode.class);
            default:
                return super.typeFromId(context, id);
        }
    }
}
