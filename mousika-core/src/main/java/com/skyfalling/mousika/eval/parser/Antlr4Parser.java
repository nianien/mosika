package com.skyfalling.mousika.eval.parser;

import com.nianien.antlr4.RuleLexer;
import com.nianien.antlr4.RuleParser;
import com.nianien.antlr4.RuleVisitor;
import com.skyfalling.mousika.eval.node.RuleNode;
import org.antlr.v4.runtime.*;

/**
 * 基于Antlr4的规则解析
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class Antlr4Parser {

    public static RuleNode parse(String expression, NodeGenerator generator) {
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
