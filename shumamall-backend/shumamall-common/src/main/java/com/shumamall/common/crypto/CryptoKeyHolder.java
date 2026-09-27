package com.shumamall.common.crypto;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * 敏感字段加密密钥持有者。
 * <p>
 * 由 {@link CryptoConfig} 在应用启动时从配置中心注入密钥，供 MyBatis
 * {@link EncryptTypeHandler} 静态调用（TypeHandler 由 MyBatis 反射实例化，无法依赖注入）。
 * <p>
 * 未配置密钥时 {@code key} 保持为空，加解密退化为原样透传，仅打一条 WARN，
 * 避免本地未接配置中心的环境无法启动业务服务。
 */
@Slf4j
public final class CryptoKeyHolder {

    /** AES-256 要求 32 字节密钥 */
    private static final int KEY_LENGTH = 32;

    private static volatile SecretKey key;

    private CryptoKeyHolder() {
    }

    /**
     * 初始化密钥（应用启动时调用一次）。
     *
     * @param base64Key 32 字节密钥的 Base64 编码；为空表示不启用加密
     */
    public static void init(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("未配置 shumamall.crypto.aes-key，敏感字段加密未启用（按明文存储）");
            return;
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("shumamall.crypto.aes-key 不是合法的 Base64 字符串", e);
        }
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalStateException(
                    "shumamall.crypto.aes-key 必须为 " + KEY_LENGTH + " 字节（AES-256）的 Base64 编码，当前长度: " + keyBytes.length);
        }
        key = new SecretKeySpec(keyBytes, "AES");
        log.info("敏感字段加密已启用：算法=AES-256-CBC，随机 IV，密文前缀={}", AesUtils.CIPHER_PREFIX);
    }

    /** 加密（未启用时原样返回） */
    static String encrypt(String plainText) {
        SecretKey currentKey = key;
        return currentKey == null ? plainText : AesUtils.encrypt(plainText, currentKey);
    }

    /** 解密（未启用时原样返回） */
    static String decrypt(String cipherText) {
        SecretKey currentKey = key;
        return currentKey == null ? cipherText : AesUtils.decrypt(cipherText, currentKey);
    }
}
