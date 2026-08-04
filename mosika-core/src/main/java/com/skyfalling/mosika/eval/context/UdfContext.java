package com.skyfalling.mosika.eval.context;

/**
 * 规则上下文接口定义
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public interface UdfContext {

    /**
     * 规则评估参数
     */
    Object getData();


    /**
     * 获取当前规则
     */
    String getRule();


    /**
     * 获取属性
     */
    Object getProperty(Object name);

    /**
     * 添加属性
     */
    void setProperty(String name, Object value);


    /**
     * 移除属性
     */
    void removeProperty(String name);
}