package com.skyfalling.mosika.eval.parser;


import com.skyfalling.mosika.eval.listener.ListenerProvider;
import com.skyfalling.mosika.eval.listener.RuleEvent;
import com.skyfalling.mosika.eval.listener.RuleEvent.EventType;
import com.skyfalling.mosika.eval.node.CaseNode;
import com.skyfalling.mosika.eval.node.RuleNode;
import com.skyfalling.mosika.exception.RuleParseException;

/**
 * 构建决策节点
 * Created on 2022/2/18
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class NodeBuilder {

    /** 默认解析状态整体替换，保证并发解析不会混用新旧生成器。 */
    private static volatile ParserState defaultState = new ParserState(NodeGenerator.create());

    /**
     * 设置节点生成器
     *
     * @param generator
     */
    public static synchronized void setGenerator(NodeGenerator generator) {
        if (generator != null) {
            defaultState = new ParserState(generator);
        }
    }

    /**
     * 解析表达式。
     * <p>套件执行路径由 {@code RuleEvaluator} 维护实例级缓存；这里不缓存并返回共享 AST，
     * 避免调用方组合节点时污染后续解析结果。</p>
     */
    public static RuleNode build(String expr) {
        ParserState state = defaultState;
        return build(expr, state.generator);
    }

    /**
     * 使用指定生成器解析表达式，不读取或改写进程级默认生成器。
     * <p>
     * 规则套件使用此入口把复合规则解析配置限制在各自实例内；缓存由调用方持有，
     * 避免候选套件构建或另一套规则配置污染当前运行中的套件。
     */
    public static RuleNode build(String expr, NodeGenerator generator) {
        long begin = System.currentTimeMillis();
        try {
            RuleNode node = Antlr4Parser.parse(expr, generator);
            long end = System.currentTimeMillis();
            ListenerProvider.DEFAULT.onParse(new RuleEvent(EventType.PARSE_SUCCEED, expr, node, end - begin));
            return node;
        } catch (Exception e) {
            long end = System.currentTimeMillis();
            ListenerProvider.DEFAULT.onParse(new RuleEvent(EventType.PARSE_FAIL, expr, e, end - begin));
            throw new RuleParseException(expr, "rule parse failed:" + expr, e);
        }
    }


    /**
     * @param expr 条件节点
     * @param lhs  左分支节点
     */
    public static RuleNode build(String expr, String lhs) {
        return new CaseNode(build(expr), build(lhs), null);
    }

    /**
     * @param expr 条件节点
     * @param lhs  左分支节点
     * @param rhs  右分支节点
     */
    public static RuleNode build(String expr, String lhs, String rhs) {
        return new CaseNode(build(expr), build(lhs), build(rhs));
    }

    private static final class ParserState {
        private final NodeGenerator generator;

        private ParserState(NodeGenerator generator) {
            this.generator = generator;
        }
    }

}
