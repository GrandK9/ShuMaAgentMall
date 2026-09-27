package com.shumamall.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 敏感字段加密自动配置。
 * <p>
 * 密钥只从配置中心（Nacos 共享配置 {@code shumamall-common.yaml}）或环境变量读取，
 * 代码仓库不落密钥；配置项：{@code shumamall.crypto.aes-key}（32 字节 AES-256 密钥的 Base64 编码）。
 */
@Configuration
public class CryptoConfig {

    public CryptoConfig(@Value("${shumamall.crypto.aes-key:}") String aesKey) {
        CryptoKeyHolder.init(aesKey);
    }
}
