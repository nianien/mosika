package com.skyfalling.mosika.udf;

import com.skyfalling.mosika.annotation.Udf;
import com.skyfalling.mosika.eval.context.UdfContext;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Udf(group = "policy")
@NoArgsConstructor
@AllArgsConstructor
public class SystemAdminUdf implements BiFunction<String, UdfContext, Boolean> {

    private String admin;

    @Override
    public Boolean apply(String name, UdfContext ruleContext) {
        System.out.println("@@@@" + ruleContext.getRule());
        ruleContext.setProperty("admin", admin);
        return Objects.equals(name, admin);
    }

}
