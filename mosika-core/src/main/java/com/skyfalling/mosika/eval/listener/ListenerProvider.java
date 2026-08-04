package com.skyfalling.mosika.eval.listener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 监听器驱动
 * Created on 2022/2/15
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class ListenerProvider implements RuleListener {

    private static final Logger LOGGER = Logger.getLogger(ListenerProvider.class.getName());

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
            try {
                listener.onParse(event);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "rule parse listener failed: " + listener.getClass().getName(), e);
            }
        }
    }

    @Override
    public void onEval(RuleEvent event) {
        for (RuleListener listener : listeners) {
            try {
                listener.onEval(event);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "rule eval listener failed: " + listener.getClass().getName(), e);
            }
        }
    }


    /**
     * 注册监听器
     */
    public void register(RuleListener listener) {
        this.listeners.add(listener);
    }

}
