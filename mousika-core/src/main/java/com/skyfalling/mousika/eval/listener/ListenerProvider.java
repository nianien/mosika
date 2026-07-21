package com.skyfalling.mousika.eval.listener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 监听器驱动
 * Created on 2022/2/15
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class ListenerProvider implements RuleListener {

    /**
     * 默认单例
     */
    public static final ListenerProvider DEFAULT = new ListenerProvider();

    /**
     * 注册的监听器列表
     */
    private List<RuleListener> listeners = new CopyOnWriteArrayList<>();


    @Override
    public void onParse(RuleEvent event) {
        for (RuleListener listener : listeners) {
            listener.onParse(event);
        }
    }

    @Override
    public void onEval(RuleEvent event) {
        for (RuleListener listener : listeners) {
            listener.onEval(event);
        }
    }


    /**
     * 注册监听器
     */
    public void register(RuleListener listener) {
        this.listeners.add(listener);
    }

}
