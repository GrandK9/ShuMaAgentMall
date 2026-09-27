package com.shumamall.agent.guard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.planning.PlanStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 高风险操作确认凭据服务（Human-in-the-Loop 的服务端凭据签发与校验）。
 * <p>
 * 为什么不能直接用 stepId 作为确认凭据：stepId 由规划器生成（"step-1"、"step-2"），
 * 完全可预测，调用方只要在请求里塞一个 {@code confirmedStepIds=["step-1"]} 就能绕过二次确认，
 * 让"人工确认"形同虚设。因此确认凭据必须由服务端签发并满足：
 * <ul>
 *   <li><b>不可伪造</b>：HMAC-SHA256 签名，密钥只在服务端</li>
 *   <li><b>不可挪作他用</b>：绑定 sessionId + stepId + 工具名 + 操作内容摘要，
 *       防止"确认了查询操作，却用于执行下单操作"</li>
 *   <li><b>不可重放</b>：Redis 一次性消费（验证通过即作废），同一凭据无法再次触发执行</li>
 *   <li><b>不可长期持有</b>：5 分钟有效期，过期需重新发起确认</li>
 * </ul>
 * 凭据格式：{@code base64url(payload) + "." + base64url(HMAC-SHA256(payload))}。
 */
@Slf4j
@Component
public class ConfirmTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Redis 已消费凭据标记前缀，保证一次性语义 */
    private static final String CONSUMED_KEY_PREFIX = "agent:confirm:used:";

    /** 凭据有效期（秒）；待确认计划缓存的 TTL 与之一致（见 HighRiskActionGuard） */
    public static final long TTL_SECONDS = 300L;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final SecretKeySpec hmacKey;

    public ConfirmTokenService(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               @Value("${shumamall.agent.guard.confirm-secret:${shumamall.auth.jwt.secret}}") String secret) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    /**
     * 为待确认的高风险步骤签发一次性确认凭据。
     *
     * @param sessionId 当前会话 ID（凭据绑定会话，跨会话不可用）
     * @param step      待确认步骤
     * @return 确认凭据字符串
     */
    public String issue(String sessionId, PlanStep step) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sid", sessionId);
        payload.put("step", step.getStepId());
        payload.put("tool", step.getToolName());
        payload.put("op", operationDigest(step));
        payload.put("exp", System.currentTimeMillis() / 1000 + TTL_SECONDS);
        try {
            String body = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            return body + "." + ENCODER.encodeToString(sign(body));
        } catch (Exception e) {
            throw new IllegalStateException("确认凭据签发失败", e);
        }
    }

    /**
     * 校验并消费确认凭据（校验全部通过后才写入消费标记，保证失败不消耗凭据）。
     *
     * @param sessionId 当前会话 ID
     * @param step      当前计划中待确认的步骤
     * @param token     前端回传的凭据
     * @return true 表示凭据有效且本次消费成功；false 表示无效/过期/不匹配/已使用
     */
    public boolean verifyAndConsume(String sessionId, PlanStep step, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            return false;
        }
        String body = token.substring(0, separator);
        byte[] signature;
        byte[] payloadBytes;
        try {
            signature = DECODER.decode(token.substring(separator + 1));
            payloadBytes = DECODER.decode(body);
        } catch (IllegalArgumentException e) {
            log.warn("确认凭据格式非法: sessionId={}", sessionId);
            return false;
        }
        // 1. 验签：常量时间比较，避免时序侧信道
        if (!MessageDigest.isEqual(sign(body), signature)) {
            log.warn("确认凭据签名校验失败: sessionId={}", sessionId);
            return false;
        }
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(payloadBytes, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("确认凭据载荷解析失败: sessionId={}", sessionId);
            return false;
        }
        // 2. 有效期
        long exp = toLong(payload.get("exp"));
        if (exp <= System.currentTimeMillis() / 1000) {
            log.warn("确认凭据已过期: sessionId={}, stepId={}", sessionId, step.getStepId());
            return false;
        }
        // 3. 绑定关系：会话 + 步骤 + 工具 + 操作内容，任一不符即拒绝
        if (!sessionId.equals(payload.get("sid"))
                || !step.getStepId().equals(payload.get("step"))
                || !step.getToolName().equals(payload.get("tool"))
                || !operationDigest(step).equals(payload.get("op"))) {
            log.warn("确认凭据与当前操作不匹配: sessionId={}, stepId={}, tool={}",
                    sessionId, step.getStepId(), step.getToolName());
            return false;
        }
        // 4. 一次性消费：同一凭据只能触发一次执行，防重放
        String consumedKey = CONSUMED_KEY_PREFIX + sha256Hex(token);
        Boolean firstUse = redisTemplate.opsForValue()
                .setIfAbsent(consumedKey, "1", Duration.ofSeconds(TTL_SECONDS));
        if (!Boolean.TRUE.equals(firstUse)) {
            log.warn("确认凭据已被使用，拒绝重放: sessionId={}, stepId={}", sessionId, step.getStepId());
            return false;
        }
        log.info("确认凭据校验通过并已消费: sessionId={}, stepId={}, tool={}",
                sessionId, step.getStepId(), step.getToolName());
        return true;
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("确认凭据签名失败", e);
        }
    }

    /** 操作内容摘要：工具名 + 规范化参数，确保凭据绑定的是"这一次具体操作" */
    private String operationDigest(PlanStep step) {
        Map<String, Object> params = step.getParams();
        String canonicalParams = params == null || params.isEmpty()
                ? ""
                : new TreeMap<>(params).entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("&"));
        return sha256Hex(step.getToolName() + "|" + canonicalParams);
    }

    private String sha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("摘要计算失败", e);
        }
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.MIN_VALUE;
    }
}
