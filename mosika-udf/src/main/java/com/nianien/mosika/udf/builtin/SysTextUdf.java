package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;

import java.text.Normalizer;

/**
 * 文本处理内置 UDF，调用形如 {@code sys.text.matches(s, re)}。
 * <p>
 * 提供 Java 正则语义、Unicode 归一化和按码点计数等 JavaScript 不便处理的能力。
 */
@Udf(group = "sys", value = "text")
public class SysTextUdf {

    @UdfMethod(description = "整串是否匹配 Java 正则")
    public boolean matches(String text, String regex) {
        return text.matches(regex);
    }

    @UdfMethod(description = "按 Java 正则替换全部匹配")
    public String replaceAll(String text, String regex, String replacement) {
        return text.replaceAll(regex, replacement);
    }

    @UdfMethod(description = "Unicode NFC 归一化")
    public String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    @UdfMethod(description = "码点数量，正确统计 emoji 与 CJK")
    public int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    @UdfMethod(description = "左侧用 pad 补足到 length")
    public String padLeft(String text, int length, String pad) {
        if (text.length() >= length || pad.isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        while (builder.length() + text.length() < length) {
            builder.append(pad);
        }
        return builder.substring(0, length - text.length()) + text;
    }

    @UdfMethod(description = "右侧用 pad 补足到 length")
    public String padRight(String text, int length, String pad) {
        if (text.length() >= length || pad.isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text);
        while (builder.length() < length) {
            builder.append(pad);
        }
        return builder.substring(0, length);
    }

    @UdfMethod(description = "重复 count 次")
    public String repeat(String text, int count) {
        return text.repeat(count);
    }
}
