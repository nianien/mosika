package com.skyfalling.mosika.eval.parser;


import com.skyfalling.mosika.eval.node.CompositeNode;
import com.skyfalling.mosika.eval.node.ExprNode;
import com.skyfalling.mosika.eval.node.RuleNode;

import java.util.Map;
import java.util.Stack;
import java.util.function.Function;


/**
 * 节点生成器
 * Created on 2022/6/17
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public interface NodeGenerator extends Function<String, RuleNode> {

    /**
     * 支持规则解析
     */
    static NodeGenerator create() {
        return expr -> new ExprNode(expr);
    }


    /**
     * 支持复合规则递归解析
     *
     * @param compositeRules 复合规则定义
     */
    static NodeGenerator create(Map<String, String> compositeRules) {
        return compositeRules == null || compositeRules.isEmpty() ? create() :
                new NodeGenerator() {
                    @Override
                    public RuleNode apply(String s) {
                        return Antlr4Parser.parse(s, expr -> parseRecursively(expr, new Stack<>()));
                    }

                    /**
                     *
                     * @param expr 当前表达式
                     * @param resolved 已经解析的表达式
                     * @return
                     */
                    private RuleNode parseRecursively(String expr, Stack<String> resolved) {
                        //expr是一个复合规则
                        if (compositeRules.containsKey(expr)) {
                            try {
                                resolved.push(expr);
                                return new CompositeNode(expr, Antlr4Parser.parse(compositeRules.get(expr), s -> {
                                    if (resolved.contains(s)) {
                                        throw new IllegalStateException(
                                                "circular dependency between composite rules [" + expr + "] and [" + s + "]");
                                    }
                                    return parseRecursively(s, resolved);
                                }));
                            } finally {
                                //回溯算法,出栈
                                resolved.pop();
                            }
                        }
                        return new ExprNode(expr);
                    }
                };
    }
}
