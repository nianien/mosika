package com.nianien.mosika.udf.builtin;

import com.nianien.mosika.annotation.Udf;
import com.nianien.mosika.annotation.UdfMethod;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 哈希与编码内置 UDF，调用形如 {@code sys.crypto.sha256(s)}。
 * <p>
 * Rhino 没有加密能力，本组提供纯 Java 的摘要、HMAC 与 Base64 编解码。
 */
@Udf(group = "sys", value = "crypto")
public class SysCryptoUdf {

    @UdfMethod(description = "MD5 十六进制摘要")
    public String md5(String text) {
        return digest("MD5", text);
    }

    @UdfMethod(description = "SHA-1 十六进制摘要")
    public String sha1(String text) {
        return digest("SHA-1", text);
    }

    @UdfMethod(description = "SHA-256 十六进制摘要")
    public String sha256(String text) {
        return digest("SHA-256", text);
    }

    @UdfMethod(description = "SHA-512 十六进制摘要")
    public String sha512(String text) {
        return digest("SHA-512", text);
    }

    @UdfMethod(description = "HmacSHA256 十六进制摘要")
    public String hmacSha256(String text, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hmacSha256 failed", e);
        }
    }

    @UdfMethod(description = "Base64 编码 UTF-8 文本")
    public String base64Encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @UdfMethod(description = "Base64 解码为 UTF-8 文本")
    public String base64Decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private static String digest(String algorithm, String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " unavailable", e);
        }
    }
}
