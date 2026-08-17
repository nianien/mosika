package com.nianien.mosika.udf;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.udf.Functions.Function2;
import com.nianien.mosika.udf.Functions.Function3;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 
 * Created on 2022-08-26
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@Udf(group = "sys")
public class HelloWithGenericUdf implements Function2<String, Map<String, Object>, Object>, Function3<String, List<String>, Map<String, Object>, Object> {

    @Override
    public Object apply(String name, Map<String, Object> properties) {
        System.out.println("Hello " + name);
        StringBuilder result = new StringBuilder("This is " + name);
        for (Entry<String, Object> entry : properties.entrySet()) {
            result.append(" k:").append(entry.getKey()).append(" v:").append(entry.getValue()).append(";");
        }
        return result.toString();
    }

    @Override
    public Object apply(String name, List<String> people, Map<String, Object> properties) {
        System.out.println("Hello " + name);
        StringBuilder result = new StringBuilder("This is " + name);
        result.append(" Outstanding person : ").append(people);
        for (Entry<String, Object> entry : properties.entrySet()) {
            result.append(" k:").append(entry.getKey()).append(" v:").append(entry.getValue()).append(";");
        }
        return result.toString();
    }
}
