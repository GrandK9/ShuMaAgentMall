package com.shumamall.common.sign;

import com.shumamall.common.auth.JwtUtils;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResponseWriter;
import com.shumamall.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * 请求签名 + 防重放过滤器。
 * <p>
 * 校验三个请求头：
 * <pre>
 *   X-Timestamp  epoch 毫秒，与服务端时差必须 ≤ {@code shumamall.sign.window-seconds}（默认 300 秒）
 *   X-Nonce      一次性随机串，同一用户内不可重复（Redis SET NX EX 消费）
 *   X-Sign       Base64(HMAC-SHA256(userSignSecret, canonical))，见 {@link SignUtils#canonical}
 * </pre>
 * <p>
 * <b>只拦写方法</b>：GET/HEAD/OPTIONS 直接放行。签名保护的是「改数据」和「花钱」，
 * 给高频读接口加一层 HMAC 只会白白增加延迟，读接口本身无副作用、被重放也改不了任何东西。
 * <p>
 * <b>校验顺序是刻意安排的</b>：
 * <ol>
 *   <li>先看时间戳 —— 最便宜，能在做任何加密计算前挡掉明显过期的请求；</li>
 *   <li>再验签名 —— 只有签名正确才允许消费 nonce；</li>
 *   <li>最后消费 nonce —— 若反过来先消费，攻击者用一堆伪造签名的请求就能把合法用户即将使用的 nonce
 *       提前占掉，导致正常下单全被拒（拒绝服务）。</li>
 * </ol>
 */
@Slf4j
public class RequestSignFilter implements Filter {

    /** 签名头：Base64(HMAC-SHA256) */
    public static final String HEADER_SIGN = "X-Sign";
    /** 签名头：epoch 毫秒 */
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    /** 签名头：一次性随机串 */
    public static final String HEADER_NONCE = "X-Nonce";

    /** 需要校验签名的请求方法：只拦写操作 */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final String NONCE_KEY_PREFIX = "sign:nonce:";

    private final JwtUtils jwtUtils;
    private final SignSecretResolver signSecretResolver;
    private final StringRedisTemplate redisTemplate;

    /** 允许的时间偏差（秒） */
    private final long windowSeconds;

    public RequestSignFilter(JwtUtils jwtUtils,
                             SignSecretResolver signSecretResolver,
                             StringRedisTemplate redisTemplate,
                             long windowSeconds) {
        this.jwtUtils = jwtUtils;
        this.signSecretResolver = signSecretResolver;
        this.redisTemplate = redisTemplate;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (!WRITE_METHODS.contains(request.getMethod().toUpperCase())) {
            chain.doFilter(request, response);
            return;
        }

        // 验签要先读 body，读过的流不能再用，包一层缓存供 Controller 重复读
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        String authHeader = cachedRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED,
                    "请求签名需要登录身份，未提供认证令牌");
            return;
        }

        Claims claims = jwtUtils.parseToken(authHeader.substring(7));
        if (claims == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.TOKEN_INVALID, "无效的认证令牌");
            return;
        }

        Long userId = claims.get("user_id", Long.class);
        String userSignSecret = signSecretResolver.resolveFromClaims(claims);
        if (userId == null || userSignSecret == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.TOKEN_INVALID,
                    "认证令牌缺少签发信息，请重新登录后再操作");
            return;
        }

        String timestamp = cachedRequest.getHeader(HEADER_TIMESTAMP);
        String nonce = cachedRequest.getHeader(HEADER_NONCE);
        String clientSign = cachedRequest.getHeader(HEADER_SIGN);
        if (isBlank(timestamp) || isBlank(nonce) || isBlank(clientSign)) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, ResultCode.SIGN_MISSING,
                    "缺少请求签名头（X-Sign / X-Timestamp / X-Nonce）");
            return;
        }

        long timestampMillis;
        try {
            timestampMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, ResultCode.SIGN_INVALID,
                    "X-Timestamp 必须是 epoch 毫秒");
            return;
        }
        long driftSeconds = Math.abs(System.currentTimeMillis() - timestampMillis) / 1000;
        if (driftSeconds > windowSeconds) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, ResultCode.SIGN_EXPIRED,
                    "请求时间戳与服务端相差 " + driftSeconds + " 秒，超出 " + windowSeconds + " 秒时间窗");
            return;
        }

        String canonical = SignUtils.canonical(cachedRequest.getMethod(), cachedRequest.getRequestURI(),
                cachedRequest.getQueryString(), timestamp, nonce, cachedRequest.getBodyAsString());
        String serverSign = SignUtils.sign(userSignSecret, canonical);
        if (!SignUtils.matches(serverSign, clientSign)) {
            log.warn("请求签名校验失败: userId={}, method={}, uri={}, query={}", userId,
                    cachedRequest.getMethod(), cachedRequest.getRequestURI(), cachedRequest.getQueryString());
            reject(response, HttpServletResponse.SC_BAD_REQUEST, ResultCode.SIGN_INVALID,
                    "请求签名校验失败，请求内容可能已被篡改");
            return;
        }

        if (!consumeNonce(response, userId, nonce)) {
            return;
        }

        chain.doFilter(cachedRequest, response);
    }

    /**
     * 消费 nonce 实现防重放：Redis {@code SET NX EX} 原子写入，写入成功=首次出现，失败=重复提交。
     * <p>
     * TTL 取时间窗的两倍：超过时间窗的请求本来就会被时间戳校验拒绝，nonce 再留一会儿只是兜底，
     * 避免「时间窗边缘的两个请求同时到达」这种边界情况漏过。
     * <p>
     * Redis 异常时**失败关闭**（拒绝请求）。防重放是资金链路的安全保证，宁可报错让用户重试，
     * 也不能在存储不可用时静默放行——那等于防重放功能整个失效，而外部完全看不出来。
     *
     * @return true=nonce 首次出现（继续放行）；false=重复或存储异常（已写回响应）
     */
    private boolean consumeNonce(HttpServletResponse response, Long userId, String nonce) throws IOException {
        String key = NONCE_KEY_PREFIX + userId + ":" + nonce;
        Boolean firstTime;
        try {
            firstTime = redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(windowSeconds * 2));
        } catch (Exception e) {
            log.error("防重放 nonce 存储不可用, key={}", key, e);
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, ResultCode.SERVICE_UNAVAILABLE,
                    "防重放校验暂不可用，请稍后重试");
            return false;
        }
        if (!Boolean.TRUE.equals(firstTime)) {
            log.warn("检测到请求重放: userId={}, nonce={}", userId, nonce);
            reject(response, HttpServletResponse.SC_CONFLICT, ResultCode.NONCE_REPLAYED,
                    "该请求已被处理，请勿重复提交");
            return false;
        }
        return true;
    }

    private void reject(HttpServletResponse response, int httpStatus, ResultCode resultCode, String message)
            throws IOException {
        ResponseWriter.writeJson(response, httpStatus, R.failed(resultCode, message));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
