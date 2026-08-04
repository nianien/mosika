package com.skyfalling.mosika.ui.tree.resolver;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.skyfalling.mosika.ui.tree.node.TreeNode;
import com.skyfalling.mosika.ui.tree.node.flow.*;
import com.skyfalling.mosika.ui.tree.node.rule.HNode;
import com.skyfalling.mosika.ui.tree.node.rule.LNode;
import com.skyfalling.mosika.ui.tree.node.rule.RNode;
import lombok.SneakyThrows;

/**
 * 处理UI树节点类型的序列化与反序列化,支持多态
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class NodeTypeResolver extends TypeIdResolverBase {
    @Override
    public String idFromValue(Object value) {
        return value.getClass().getSimpleName().substring(0, 1).toUpperCase();
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return idFromValue(value);
    }

    @Override
    public Id getMechanism() {
        return Id.CUSTOM;
    }


    @SneakyThrows
    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        switch (id) {
            case "A":
                return SimpleType.constructUnsafe(ANode.class);
            case "C":
                return SimpleType.constructUnsafe(CNode.class);
            case "D":
                return SimpleType.constructUnsafe(DNode.class);
            case "J":
                return SimpleType.constructUnsafe(JNode.class);
            case "P":
                return SimpleType.constructUnsafe(PNode.class);
            case "S":
                return SimpleType.constructUnsafe(SNode.class);
            case "L":
                return SimpleType.constructUnsafe(LNode.class);
            case "H":
                return SimpleType.constructUnsafe(HNode.class);
            case "R":
                return SimpleType.constructUnsafe(RNode.class);
            case "T":
                return SimpleType.constructUnsafe(TreeNode.class);
            default:
                return super.typeFromId(context, id);
        }
    }
}
