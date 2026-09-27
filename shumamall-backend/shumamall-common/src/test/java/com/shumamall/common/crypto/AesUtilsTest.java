package com.shumamall.common.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感字段加解密单元测试。
 * <p>
 * 覆盖：加解密往返、随机 IV 导致的密文随机性、存量明文向后兼容、损坏密文快速失败、密钥长度校验。
 */
class AesUtilsTest {

    /** 测试专用 32 字节密钥（与生产密钥无关） */
    private static final SecretKey KEY = new SecretKeySpec(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");

    @Test
    void encryptThenDecrypt_returnsOriginal() {
        String plain = "13800138000";

        String cipher = AesUtils.encrypt(plain, KEY);

        assertTrue(cipher.startsWith(AesUtils.CIPHER_PREFIX), "密文应带版本前缀");
        assertNotEquals(plain, cipher);
        assertEquals(plain, AesUtils.decrypt(cipher, KEY));
    }

    @Test
    void encrypt_sameInput_producesDifferentCipher() {
        String plain = "13800138000";

        String first = AesUtils.encrypt(plain, KEY);
        String second = AesUtils.encrypt(plain, KEY);

        assertNotEquals(first, second, "随机 IV 应保证同一明文两次密文不同");
        assertEquals(plain, AesUtils.decrypt(first, KEY));
        assertEquals(plain, AesUtils.decrypt(second, KEY));
    }

    @Test
    void decrypt_legacyPlainText_returnsAsIs() {
        // 加密上线前写入的明文数据没有 v1: 前缀，必须原样返回，保证改造对存量数据向后兼容
        assertEquals("13800138000", AesUtils.decrypt("13800138000", KEY));
        assertEquals("tom@example.com", AesUtils.decrypt("tom@example.com", KEY));
    }

    @Test
    void decrypt_corruptedCipher_throws() {
        assertThrows(IllegalStateException.class,
                () -> AesUtils.decrypt(AesUtils.CIPHER_PREFIX + "not-base64:also-bad", KEY));
    }

    @Test
    void nullAndEmpty_passedThrough() {
        assertEquals(null, AesUtils.encrypt(null, KEY));
        assertEquals("", AesUtils.encrypt("", KEY));
        assertEquals(null, AesUtils.decrypt(null, KEY));
        assertEquals("", AesUtils.decrypt("", KEY));
    }

    @Test
    void init_wrongKeyLength_throws() {
        String tooShort = Base64.getEncoder().encodeToString("short-key".getBytes(StandardCharsets.UTF_8));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> CryptoKeyHolder.init(tooShort));

        assertTrue(ex.getMessage().contains("32"));
    }

    @Test
    void init_emptyKey_disablesEncryptionWithoutThrowing() {
        // 未配置密钥时不应阻塞服务启动，加解密退化为明文透传
        CryptoKeyHolder.init("");
    }
}
