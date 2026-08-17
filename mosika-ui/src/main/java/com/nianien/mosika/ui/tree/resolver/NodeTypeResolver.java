package com.nianien.mosika.ui.tree.resolver;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.nianien.mosika.ui.tree.node.TreeNode;
import com.nianien.mosika.ui.tree.node.flow.*;
import com.nianien.mosika.ui.tree.node.rule.BNode;
import com.nianien.mosika.ui.tree.node.rule.HNode;
import com.nianien.mosika.ui.tree.node.rule.LNode;
import com.nianien.mosika.ui.tree.node.rule.RNode;
import lombok.SneakyThrows;

/**
 * 负责 UITree 节点类型标识的序列化和反序列化
 * <p>
 * 类型标识取具体节点类名的首字母，并映射到对应的节点实现类
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
        return value.getClass().getSimpleName().substring(0, 1).toUpperCase();
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
            case "A":
                return SimpleType.constructUnsafe(ANode.class);
            case "B":
                return SimpleType.constructUnsafe(BNode.class);
            case "C":
                return SimpleType.constructUnsafe(CNode.class);
            case "D":
                return SimpleType.constructUnsafe(DNode.class);
            case "H":
                return SimpleType.constructUnsafe(HNode.class);
            case "L":
                return SimpleType.constructUnsafe(LNode.class);
            case "P":
                return SimpleType.constructUnsafe(PNode.class);
            case "R":
                return SimpleType.constructUnsafe(RNode.class);
            case "S":
                return SimpleType.constructUnsafe(SNode.class);
            case "T":
                return SimpleType.constructUnsafe(TreeNode.class);
            default:
                return super.typeFromId(context, id);
        }
    }
}
