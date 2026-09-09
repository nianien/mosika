package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.udf.UdfDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置 Java UDF 的注册入口。
 * <p>
 * 内置 UDF 全部挂在保留分组 {@code sys} 下，产品层（如 mosika-web 的 {@code RuleSuiteManager}）
 * 调用 {@link #definitions()}，把内置 UDF 与业务 Java/JavaScript UDF 一起交给 Core。
 * 本模块只依赖 mosika-core，不含 Spring 装配。
 */
public final class BuiltinUdfs {

    private static final List<Object> UDFS = List.of(
            new SysNumUdf(),
            new SysDateUdf(),
            new SysCryptoUdf(),
            new SysTextUdf(),
            new SysCollUdf(),
            new SysIdUdf());

    private BuiltinUdfs() {
    }

    /**
     * 返回全部内置 UDF 定义。
     *
     * @return 内置 UDF 定义列表
     */
    public static List<UdfDefinition> definitions() {
        List<UdfDefinition> definitions = new ArrayList<>(UDFS.size());
        for (Object udf : UDFS) {
            Udf annotation = udf.getClass().getAnnotation(Udf.class);
            definitions.add(new UdfDefinition(annotation.group(), annotation.value(), udf));
        }
        return definitions;
    }
}
