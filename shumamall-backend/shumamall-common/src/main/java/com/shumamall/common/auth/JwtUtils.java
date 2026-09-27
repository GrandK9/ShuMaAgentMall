package com.shumamall.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类。
 * <p>
 * 使用 HS256 算法签发和校验 token。
 * claims 中包含 user_id 和 role_codes，不存放全量权限。
 */
@Component
public class JwtUtils {

    private final SecretKey secretKey;

    @Value("${shumamall.auth.jwt.expiration:86400}")
    private long expiration;

    public JwtUtils(@Value("${shumamall.auth.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT token。
     *
     * @param userId    用户 ID
     * @param roleCodes 角色编码列表
     * @return JWT 字符串
     */
    public String generateToken(Long userId, List<String> roleCodes) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .claim("user_id", userId)
                .claim("role_codes", roleCodes)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从 token 中解析 claims。
     *
     * @param token JWT 字符串
     * @return claims，解析失败返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 token 中提取用户 ID。
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("user_id", Long.class) : null;
    }

    /**
     * 校验 token 是否有效。
     */
    public boolean validateToken(String token) {
        Claims claims = parseToken(token);
        return claims != null && claims.getExpiration().after(new Date());
    }
}
