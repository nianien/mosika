package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 日期运算内置 UDF，基于 {@code java.time}，调用形如 {@code sys.date.plusDays(d, 3)}。
 * <p>
 * JavaScript {@code Date} 的时区与解析行为不可靠；本组以 ISO 文本 {@code yyyy-MM-dd}
 * 传入和返回日期，运算精确可预期。
 */
@Udf(group = "sys", value = "date")
public class SysDateUdf {

    @UdfMethod(description = "今天，ISO yyyy-MM-dd")
    public String today() {
        return LocalDate.now().toString();
    }

    @UdfMethod(description = "按 pattern 解析日期文本，返回 ISO yyyy-MM-dd")
    public String parse(String text, String pattern) {
        return LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern)).toString();
    }

    @UdfMethod(description = "把 ISO 日期按 pattern 格式化")
    public String format(String isoDate, String pattern) {
        return LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern(pattern));
    }

    @UdfMethod(description = "ISO 日期加 days 天")
    public String plusDays(String isoDate, int days) {
        return LocalDate.parse(isoDate).plusDays(days).toString();
    }

    @UdfMethod(description = "ISO 日期加 months 个月")
    public String plusMonths(String isoDate, int months) {
        return LocalDate.parse(isoDate).plusMonths(months).toString();
    }

    @UdfMethod(description = "ISO 日期加 years 年")
    public String plusYears(String isoDate, int years) {
        return LocalDate.parse(isoDate).plusYears(years).toString();
    }

    @UdfMethod(description = "两个 ISO 日期相差天数 end - start")
    public long daysBetween(String isoStart, String isoEnd) {
        return ChronoUnit.DAYS.between(LocalDate.parse(isoStart), LocalDate.parse(isoEnd));
    }

    @UdfMethod(description = "a 是否早于 b")
    public boolean isBefore(String isoA, String isoB) {
        return LocalDate.parse(isoA).isBefore(LocalDate.parse(isoB));
    }

    @UdfMethod(description = "a 是否晚于 b")
    public boolean isAfter(String isoA, String isoB) {
        return LocalDate.parse(isoA).isAfter(LocalDate.parse(isoB));
    }

    @UdfMethod(description = "星期几，1=周一 .. 7=周日")
    public int dayOfWeek(String isoDate) {
        return LocalDate.parse(isoDate).getDayOfWeek().getValue();
    }
}
