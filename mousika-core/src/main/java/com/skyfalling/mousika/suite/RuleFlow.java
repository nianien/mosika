package com.skyfalling.mousika.suite;

import com.skyfalling.mousika.eval.node.RuleNode;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 以树结构实现的规则流
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Data
@AllArgsConstructor
public class RuleFlow {

    /**
     * 规则流ID
     */
    private String id;

    /**
     * 规则流根节点
     */
    private RuleNode root;
}
