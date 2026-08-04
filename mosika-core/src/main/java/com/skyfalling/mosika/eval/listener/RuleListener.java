package com.skyfalling.mosika.eval.listener;

/**
 * Created on 2022/2/15
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public interface RuleListener {


    /**
     * 规则解析事件
     */
    void onParse(RuleEvent event);

    /**
     * 规则评估事件
     */
    void onEval(RuleEvent event);
}