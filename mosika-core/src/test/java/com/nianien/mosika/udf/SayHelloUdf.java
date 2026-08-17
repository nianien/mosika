package com.nianien.mosika.udf;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.udf.Functions.Function1;
import lombok.NoArgsConstructor;

/**
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Udf(group = "policy")
@NoArgsConstructor
public class SayHelloUdf implements Function1<String, Object> {

    @Override
    public Object apply(String user) {
        System.out.println("say hello to: " + user + "!");
        return user;
    }

}
