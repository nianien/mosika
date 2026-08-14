package com.skyfalling.mosika.udf;

import com.skyfalling.mosika.eval.context.UdfContext;
import com.skyfalling.mosika.udf.Functions.Function3;

/**
 * Created on 2022/2/14
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class AdultValidateUdf implements Function3<String, Integer, UdfContext, Boolean> {


    private int minAge;

    public AdultValidateUdf(int minAge) {
        this.minAge = minAge;
    }

    @Override
    public Boolean apply(String name, Integer age, UdfContext ruleContext) {
        System.out.println("@@@@current rule:" + ruleContext.getRule());
        ruleContext.put("minAge", minAge);
        return age > minAge;
    }
}
