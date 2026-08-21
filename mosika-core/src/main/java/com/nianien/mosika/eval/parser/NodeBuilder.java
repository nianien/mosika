package com.nianien.mosika.eval.parser;

import com.nianien.antlr4.RuleLexer;
import com.nianien.antlr4.RuleParser;
import com.nianien.antlr4.RuleVisitor;
import com.nianien.mosika.engine.RuleDefinition;
import com.nianien.mosika.eval.node.CompositeNode;
import com.nianien.mosika.eval.node.ExprNode;
import com.nianien.mosika.eval.node.RuleNode;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 基于 ANTLR4 的规则 DSL 节点构建器
 * <p>
 * 使用规则定义构造时会递归编译全部命名复合规则并检测循环引用
 * 缓存仅保存解析过程中的普通命名规则和命名复合规则
 * 临时 DSL 组合的根节点不进入缓存
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@RequiredArgsConstructor
public class NodeBuilder {


    /**
     * 把解析器识别出的单个规则 ID 转换为命名规则节点
     */
    private final NodeGenerator generator;

    /**
     * 按规则 ID 缓存命名复合规则节点
     */
    private final ConcurrentMap<String, RuleNode> compiledNodes =
            new ConcurrentHashMap<>();

    /**
     * 根据完整规则列表创建节点构建器
     * <p>
     * 构造阶段收集全部复合规则后递归编译，复合规则可以引用后定义规则
     *
     * @param rules 当前套件的完整规则定义
     * @throws IllegalArgumentException 复合规则参数不合法时抛出
     * @throws IllegalStateException    复合规则语法错误或存在循环引用时抛出
     */
    public NodeBuilder(List<RuleDefinition> rules) {
        Map<String, String> compositeRules = rules.stream()
                .filter(r -> r.getRuleType() == RuleDefinition.RULE_TYPE_COMPOSITE)
                .collect(Collectors.toMap(
                        RuleDefinition::getRuleId,
                        RuleDefinition::getExpression,
                        (existingValue, newValue) -> existingValue
                ));
        this.generator = create(compositeRules);
        compositeRules.keySet().forEach(this::build);
    }

    /**
     * 创建不包含复合规则定义的节点构建器
     * <p>
     * 解析器识别出的规则 ID 会生成 {@link ExprNode}
     */
    public NodeBuilder() {
        this(Collections.EMPTY_LIST);
    }

    /**
     * 构建规则 ID 或 DSL 表达式对应的规则节点
     * <p>
     * 命中命名规则缓存时直接返回已有节点
     * 完整 DSL 表达式每次解析，表达式中的命名规则节点可以复用
     *
     * @param expr 规则 ID 或 DSL 表达式
     * @return 规则节点
     * @throws IllegalArgumentException DSL 节点参数不合法时抛出
     * @throws IllegalStateException    DSL 词法或语法错误时抛出
     */
    public RuleNode build(String expr) {
        RuleNode compiled = compiledNodes.get(expr);
        if (compiled != null) {
            return compiled;
        }
        return parse(expr, generator);
    }


    /**
     * 创建支持命名复合规则递归解析的节点生成器
     * <p>
     * 复合规则 ID 生成保留规则 ID 边界的 {@link CompositeNode}
     * 未命中复合规则索引的 ID 生成 {@link ExprNode}
     *
     * @param compositeRules 复合规则 ID 与 DSL 表达式索引
     * @return 支持命名复合规则递归解析的节点生成器
     */
    private NodeGenerator create(Map<String, String> compositeRules) {
        if (compositeRules == null || compositeRules.isEmpty()) {
            return (ruleId, arguments) -> new ExprNode(ruleId, arguments);
        }
        return new NodeGenerator() {
            @Override
            public RuleNode apply(String ruleId, String arguments) {
                return parseRecursively(ruleId, arguments, new ArrayDeque<>());
            }

            /**
             * 解析单个规则 ID，递归展开命名复合规则并检测当前解析路径中的循环引用
             *
             * @param ruleId    当前规则 ID
             * @param resolving 当前复合规则解析路径
             * @return 命名规则节点
             * @throws IllegalStateException 当前解析路径出现重复复合规则时抛出
             */
            private RuleNode parseRecursively(String ruleId, String arguments, Deque<String> resolving) {
                //复合规则只有ID，可以复用复
                RuleNode compiled = compiledNodes.get(ruleId);
                if (compiled != null) {
                    return compiled;
                }
                String expr = compositeRules.get(ruleId);
                //不是复合规则，则按原子规则处理
                if (expr == null) {
                    return new ExprNode(ruleId, arguments);
                }

                //判断是否存在循环
                if (resolving.contains(ruleId)) {
                    throw new IllegalStateException(
                            "circular dependency between composite rules ["
                                    + resolving.peek() + "] and [" + ruleId + "]");
                }
                try {
                    resolving.push(ruleId);
                    RuleNode ruleNode = new CompositeNode(ruleId,
                            parse(expr,
                                    (subExpr, args) -> parseRecursively(subExpr, args, resolving)));
                    RuleNode existing = compiledNodes.putIfAbsent(ruleId, ruleNode);
                    return existing == null ? ruleNode : existing;
                } finally {
                    resolving.pop();
                }
            }
        };
    }


    /**
     * 使用指定节点生成器解析完整 DSL 表达式
     *
     * @param expression DSL 表达式
     * @param generator  规则 ID 节点生成器
     * @return 解析生成的规则节点
     * @throws IllegalArgumentException DSL 节点参数不合法时抛出
     * @throws IllegalStateException    DSL 词法或语法错误时抛出
     */
    private static RuleNode parse(String expression, NodeGenerator generator) {
        BaseErrorListener errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg,
                                    RecognitionException e) {
                throw new IllegalStateException("line " + line + ":" + charPositionInLine + " " + msg, e);
            }
        };
        RuleLexer lexer = new RuleLexer(CharStreams.fromString(expression));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        RuleParser parser = new RuleParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        RuleVisitor visitor = new DefaultRuleVisitor(generator);
        RuleParser.ParseContext context = parser.parse();
        return (RuleNode) visitor.visit(context.expr());
    }

}
