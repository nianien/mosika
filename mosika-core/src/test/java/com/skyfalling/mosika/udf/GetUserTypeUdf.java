package com.skyfalling.mosika.udf;

import com.skyfalling.mosika.annotation.Udf;
import com.skyfalling.mosika.eval.context.RuleContext;
import lombok.NoArgsConstructor;

import java.util.function.BiFunction;

/**
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Udf
@NoArgsConstructor
public class GetUserTypeUdf implements BiFunction<String, RuleContext, Integer> {


    @Override
    public Integer apply(String name, RuleContext context) {
        int type = findUserType(name, context);
        context.setProperty("user_type", type);
        return type;
    }


    private int findUserType(String name, RuleContext context) {
        if (name.contains("admin")) {
            context.setProperty("owner", "admin");
            return 1;
        } else {
            context.setProperty("owner", "user");
            return 2;
        }
    }

}