package com.nianien.mosika.udf;


import com.nianien.mosika.bean.User;
import com.nianien.mosika.udf.Functions.Function1;
import com.nianien.mosika.udf.Functions.Function2;

/**
 * Created on 2022/12/6
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
public class AutoCastUdf implements Function1<User, Object>, Function2<User, String, Object> {
    @Override
    public Object apply(User user) {
        System.out.println("user:" + user);
        return user.getName();
    }

    @Override
    public Object apply(User user, String name) {
        System.out.println("user:" + user + ",new name:" + name);
        return name;
    }

}
