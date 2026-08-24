package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;

@Udf(group = "javaFns", value = "strings")
public class JavaStringUdf {

    @UdfMethod(description = "判断字符串是否包含指定内容")
    public Boolean contains(String text, String part) {
        return text.contains(part);
    }

    @UdfMethod(description = "使用指定分隔符拼接字符串")
    public String join(String separator, String... values) {
        return String.join(separator, values);
    }
}
