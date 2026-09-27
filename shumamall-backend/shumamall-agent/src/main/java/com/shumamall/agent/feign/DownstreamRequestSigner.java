package com.shumamall.agent.feign;

import com.shumamall.common.sign.SignSecretResolver;
import com.shumamall.common.sign.SignUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * 下游写接口的请求签名器。
 * <p>
 * <b>为什么 Agent 需要签名</b>：order / payment 的写接口挂了 {@code RequestSignFilter}
 * （HMAC-SHA256 + 时间窗 + nonce 防重放），要求请求头带 {@code X-Sign / X-Timestamp / X-Nonce}。
 * Agent 是服务端代理，浏览器把 JWT 透传给它，它再以用户身份调下游——于是它必须像前端一样
 * 把「这次请求的内容」签上名。只透传 {@code Authorization} 会被下游以
 * {@code 4013 缺少请求签名头} 直接拒绝，Feign 抛异常后又被 CircuitBreaker 包成
 * {@code NoFallbackAvailableException}，最终只剩一句 "No fallback available."。
 * <p>
 * <b>密钥从哪来</b>：不新增配置，也不把密钥塞进对话请求。签名密钥可由
 * {@code HMAC-SHA256(jwtSecret, "shumamall-sign-v1:{userId}:{iat}")} 从 JWT 现场推导
 * （见 {@link SignSecretResolver}），Agent 手上正好有透传进来的原始 token，因此本地即可算出。
 * <p>
 * <b>为什么不在 Feign 拦截器里统一做</b>：签名必须与最终发出的字节完全一致，
 * 而 {@code RequestInterceptor} 执行时请求体尚未编码，拿不到待签名原文；且接口路径也需要
 * 单独判断哪些要签。因此改为在调用点显式签名——待签名的 body 由调用方用同一个
 * {@code ObjectMapper} 序列化，与 Feign 实际发送的内容逐字节相同。
 */
@Component
public class DownstreamRequestSigner {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SignSecretResolver signSecretResolver;

    public DownstreamRequestSigner(SignSecretResolver signSecretResolver) {
        this.signSecretResolver = signSecretResolver;
    }

    /**
     * 为一次下游写请求生成签名头。
     *
     * @param method 请求方法（GET/POST/PUT/DELETE，服务端会转大写）
     * @param path   不含查询串的路径，如 {@code /api/v1/orders}
     * @param body   与最终发送内容一致的请求体原文；无请求体传空串或 {@code null}
     * @return 签名三件套
     * @throws IllegalStateException 当前线程拿不到登录身份（token 缺失或非法），无法签名
     */
    public SignedRequest sign(String method, String path, String body) {
        String signSecret = currentUserSignSecret();
        if (signSecret == null) {
            throw new IllegalStateException("缺少登录身份，无法为下游写接口生成请求签名");
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        // nonce 一次性：下游用 Redis SET NX 消费，重复即判为重放
        String nonce = UUID.randomUUID().toString();
        String sign = SignUtils.sign(signSecret,
                SignUtils.canonical(method, path, null, timestamp, nonce, body));
        return new SignedRequest(timestamp, nonce, sign);
    }

    /**
     * 从当前请求上下文取原始 token 并推导用户签名密钥。
     * <p>
     * 工具在编排器的线程池里执行，{@code RequestContextHolder} 由 Controller
     * 在提交异步任务前显式传递（SSE 请求未完成回收，请求头仍可读）。
     *
     * @return 签名密钥；无上下文或 token 非法时返回 {@code null}
     */
    private String currentUserSignSecret() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        String authHeader = attributes.getRequest().getHeader(AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return signSecretResolver.resolveFromToken(authHeader.substring(BEARER_PREFIX.length()));
    }

    /**
     * 一次写请求的签名头三件套，直接拼到 Feign 方法的 {@code @RequestHeader} 参数上。
     *
     * @param timestamp {@code X-Timestamp}，epoch 毫秒
     * @param nonce     {@code X-Nonce}，一次性随机串
     * @param sign      {@code X-Sign}，Base64(HMAC-SHA256)
     */
    public record SignedRequest(String timestamp, String nonce, String sign) {
    }
}
