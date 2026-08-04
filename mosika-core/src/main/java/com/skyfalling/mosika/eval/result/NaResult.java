package com.skyfalling.mosika.eval.result;

/**
 * 表示无结果,用于区别null
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class NaResult extends NodeResult {

    public static final NaResult DEFAULT = new NaResult();

    private NaResult() {
    }

    @Override
    public String toString() {
        return "NaResult";
    }
}