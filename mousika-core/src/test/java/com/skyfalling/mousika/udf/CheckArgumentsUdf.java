package com.skyfalling.mousika.udf;


import com.skyfalling.mousika.annotation.Udf;
import com.skyfalling.mousika.eval.context.UdfContext;
import com.skyfalling.mousika.udf.Functions.Function4;

import java.util.Map;

/**
 * 
 * Created on 2022-08-26
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Udf(group = "sys")
public class CheckArgumentsUdf implements Function4<String, Object, UdfContext, Map<String, Object>, Object> {

    public Object apply(String id, Object param, UdfContext ruleContext, Map<String, Object> args) {
        System.out.println("CheckArgumentsUdf execute");
        return "success";
    }

}
