package com.shumamall.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-CBC 字段加解密工具。
 * <p>
 * 每字段加密时随机生成 16 字节 IV，IV 与密文一同持久化，保证同一明文多次加密结果不同
 * （防止攻击者通过密文比对推断明文，例如识别出两个用户手机号相同）。
 * <p>
 * 密文格式：{@code v1:{Base64(IV)}:{Base64(密文)}}。前缀 {@code v1:} 用于与存量明文区分，
 * 解密时遇到无前缀的值原样返回，因此加密改造对历史数据向后兼容（存量明文在下次写入时自然转为密文）。
 */
public final class AesUtils {

    /** 密文版本前缀：无此前缀的值视为存量明文 */
    public static final String CIPHER_PREFIX = "v1:";

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    /** CBC 模式 IV 固定 16 字节 */
    private static final int IV_LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AesUtils() {
    }

    /**
     * 加密明文字段。
     *
     * @param plainText 明文（null 或空串原样返回）
     * @param key       256 bit AES 密钥
     * @return {@code v1:{IV}:{密文}} 格式密文
     */
    public static String encrypt(String plainText, SecretKey key) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(cipherBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    /**
     * 解密密文字段。
     *
     * @param cipherText 密文；无 {@link #CIPHER_PREFIX} 前缀时视为存量明文原样返回
     * @param key        256 bit AES 密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, SecretKey key) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        if (!cipherText.startsWith(CIPHER_PREFIX)) {
            // 存量明文：加密上线前写入的数据，保持可读
            return cipherText;
        }
        String payload = cipherText.substring(CIPHER_PREFIX.length());
        int separator = payload.indexOf(':');
        if (separator < 0) {
            throw new IllegalStateException("敏感字段密文格式非法：缺少 IV 分隔符");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(payload.substring(0, separator));
            byte[] cipherBytes = Base64.getDecoder().decode(payload.substring(separator + 1));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("敏感字段解密失败（密钥不匹配或数据损坏）", e);
        }
    }
}
