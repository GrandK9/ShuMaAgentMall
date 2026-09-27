package com.shumamall.common.sign;

import com.shumamall.common.auth.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 用户签名密钥解析器 —— 签发方与校验方共用同一套推导逻辑。
 * <p>
 * 密钥不是固定值，也不是查库/ZSET 存出来的，而是从 JWT 里的 {@code user_id + iat} 现场推导：
 * <pre>
 *   signSecret = HMAC-SHA256(jwtSecret, "shumamall-sign-v1:{userId}:{iat}")
 * </pre>
 * <ul>
 *   <li><b>登录时</b>（auth 服务）：签发 token 后调用 {@link #resolveFromToken} 把密钥返回给客户端；
 *   <li><b>验签时</b>（order / payment 服务）：从请求头解析 token，调用同一个方法算出期望密钥。
 * </ul>
 * 两侧算法一致，因此不需要任何共享存储、不需要额外配置项，也不会出现「密钥表和 token 不同步」的问题；
 * iat 相同即密钥相同，重新登录拿到新 token 就自动换了密钥（旧密钥随旧 token 一起作废）。
 */
@Component
public class SignSecretResolver {

    private final JwtUtils jwtUtils;
    private final String jwtSecret;

    public SignSecretResolver(JwtUtils jwtUtils, @Value("${shumamall.auth.jwt.secret}") String jwtSecret) {
        this.jwtUtils = jwtUtils;
        this.jwtSecret = jwtSecret;
    }

    /**
     * 按 userId + 签发时间推导签名密钥。
     *
     * @param userId             用户 ID
     * @param issuedAtEpochSecond token 的 iat（秒级）
     * @return Base64 编码的签名密钥
     */
    public String resolve(Long userId, long issuedAtEpochSecond) {
        return SignUtils.deriveUserSecret(jwtSecret, userId, issuedAtEpochSecond);
    }

    /**
     * 从 token 解析出 userId 与 iat 后推导签名密钥。
     *
     * @param token JWT 字符串
     * @return 签名密钥；token 非法或缺 iat/user_id 时返回 {@code null}
     */
    public String resolveFromToken(String token) {
        Claims claims = jwtUtils.parseToken(token);
        if (claims == null) {
            return null;
        }
        return resolveFromClaims(claims);
    }

    /**
     * 从已解析的 claims 推导签名密钥（验签链路里已经解析过一次 token，避免重复解析）。
     *
     * @param claims JWT claims
     * @return 签名密钥；缺 iat/user_id 时返回 {@code null}
     */
    public String resolveFromClaims(Claims claims) {
        if (claims == null) {
            return null;
        }
        Date issuedAt = claims.getIssuedAt();
        Long userId = claims.get("user_id", Long.class);
        if (issuedAt == null || userId == null) {
            return null;
        }
        return resolve(userId, issuedAt.getTime() / 1000);
    }
}
