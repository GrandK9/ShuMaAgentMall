package com.shumamall.agent.guard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 用户本轮输入的规范化指纹，用于待确认计划是否与「确认前那句」一致。
 */
public final class UserMessageFingerprint {

    private UserMessageFingerprint() {
    }

    public static String normalize(String message) {
        if (message == null) {
            return "";
        }
        return message.strip().replace("\r\n", "\n");
    }

    /**
     * 对原始用户消息规范化后做 SHA-256（hex），供 Redis 缓存与比对。
     */
    public static String of(String rawUserMessage) {
        return sha256Hex(normalize(rawUserMessage));
    }

    static String sha256Hex(String normalized) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("用户消息指纹计算失败", e);
        }
    }
}
