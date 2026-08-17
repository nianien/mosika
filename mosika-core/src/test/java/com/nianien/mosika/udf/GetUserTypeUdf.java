package com.nianien.mosika.udf;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.eval.context.RuleContext;
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
        context.put("user_type", type);
        return type;
    }


    private int findUserType(String name, RuleContext context) {
        if (name.contains("admin")) {
            context.put("owner", "admin");
            return 1;
        } else {
            context.put("owner", "user");
            return 2;
        }
    }

}
