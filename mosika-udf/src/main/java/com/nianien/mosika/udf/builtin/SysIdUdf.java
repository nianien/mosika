package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 标识与随机内置 UDF，调用形如 {@code sys.id.uuid()}。
 * <p>
 * 基于 {@link SecureRandom} 与 {@link UUID}，提供 JavaScript 侧不便获得的强随机标识。
 */
@Udf(group = "sys", value = "id")
public class SysIdUdf {

    private static final SecureRandom RANDOM = new SecureRandom();

    @UdfMethod(description = "带连字符的 UUID")
    public String uuid() {
        return UUID.randomUUID().toString();
    }

    @UdfMethod(description = "不带连字符的 UUID")
    public String uuidHex() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @UdfMethod(description = "闭区间 [minInclusive, maxInclusive] 内的随机整数")
    public int randomInt(int minInclusive, int maxInclusive) {
        return RANDOM.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
    }
}
