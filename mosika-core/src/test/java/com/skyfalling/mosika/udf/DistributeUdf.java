package com.skyfalling.mosika.udf;

import com.skyfalling.mosika.annotation.Udf;
import lombok.NoArgsConstructor;

import java.util.function.BiFunction;

/**
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Udf
@NoArgsConstructor
public class DistributeUdf implements BiFunction<String, String, Boolean> {

    @Override
    public Boolean apply(String user, String owner) {
        System.out.println("账号[" + user + "]分配角色[" + owner + "]!");
        return true;
    }

}
