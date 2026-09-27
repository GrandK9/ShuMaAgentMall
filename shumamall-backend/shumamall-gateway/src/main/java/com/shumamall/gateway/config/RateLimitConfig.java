package com.shumamall.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 网关限流维度配置。
 * <p>
 * 为 {@code RequestRateLimiter} 提供限流键：登录用户按用户维度、匿名请求按客户端 IP 维度。
 * 令牌桶（Redis）的读写与判定由 Gateway 内置的 {@code RedisRateLimiter} 完成，本类只负责"算 key"。
 * <p>
 * 之所以在这里自行校验 JWT 签名而不是直接取 token 里的 user_id 当 key：
 * 限流键若取自未校验的 token，攻击者每次请求伪造一个不同的 user_id 即可让每个请求
 * 落入独立令牌桶，限流形同虚设。此处用与业务服务相同的 HS256 密钥验签，
 * 验签失败的 token 一律降级为 IP 维度（等同于匿名请求）。
 */
@Slf4j
@Configuration
public class RateLimitConfig {

    /**
     * 限流键解析器：优先用户维度，无法确定身份时退回 IP 维度。
     *
     * @param jwtSecret JWT 签名密钥（与业务服务共用同一份 Nacos 共享配置）
     * @return 限流键解析器
     */
    @Bean
    public KeyResolver userOrIpKeyResolver(@Value("${shumamall.auth.jwt.secret:}") String jwtSecret) {
        SecretKey secretKey = jwtSecret.isBlank() ? null : Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        if (secretKey == null) {
            log.warn("未配置 shumamall.auth.jwt.secret，网关限流降级为仅按 IP 维度计数");
        }
        return exchange -> {
            Long userId = resolveUserId(exchange, secretKey);
            return Mono.just(userId != null ? "user:" + userId : "ip:" + resolveClientIp(exchange));
        };
    }

    /**
     * 从 Authorization 头解析并验签取出用户 ID。
     *
     * @param exchange  当前请求
     * @param secretKey JWT 密钥，为 null 表示未配置
     * @return 用户 ID；未携带 token、验签失败或已过期时返回 null
     */
    private Long resolveUserId(ServerWebExchange exchange, SecretKey secretKey) {
        if (secretKey == null) {
            return null;
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(authHeader.substring(7))
                    .getPayload();
            return claims.get("user_id", Long.class);
        } catch (Exception e) {
            // 无效/过期 token 不参与用户维度计数，退化为 IP 维度，避免被用作绕过限流的手段
            return null;
        }
    }

    /**
     * 取客户端 IP。
     * <p>
     * 直接用 TCP 连接的远端地址而<b>不</b>读 {@code X-Forwarded-For}：该头可被客户端随意伪造，
     * 用它当限流键等于把限流开关交给调用方。若后续在前面部署了可信 Nginx，
     * 应换成 {@code XForwardedRemoteAddressResolver.maxTrustedIndex(n)} 并限定可信跳数。
     *
     * @param exchange 当前请求
     * @return 客户端 IP，取不到时返回 "unknown"
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }
}
