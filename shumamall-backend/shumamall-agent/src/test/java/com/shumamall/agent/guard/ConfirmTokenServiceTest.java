package com.shumamall.agent.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.planning.PlanStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 确认凭据服务单测：覆盖不可伪造 / 不可挪作他用 / 不可重放 / 不可长期持有四条安全约束。
 */
class ConfirmTokenServiceTest {

    private static final String SECRET = "unit-test-confirm-secret-key-256-bits-long-for-hs256";
    private static final String SESSION_ID = "sess-1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConfirmTokenService service;

    /** 模拟 Redis：setIfAbsent 只在首次返回 true，用于验证一次性消费 */
    private final Set<String> usedKeys = ConcurrentHashMap.newKeySet();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> usedKeys.add(invocation.getArgument(0)));
        service = new ConfirmTokenService(redisTemplate, objectMapper, SECRET);
    }

    private PlanStep step(String stepId, String toolName, Map<String, Object> params) {
        PlanStep step = new PlanStep();
        step.setStepId(stepId);
        step.setToolName(toolName);
        step.setParams(params);
        return step;
    }

    @Test
    void 签发后凭据可用_且仅能消费一次() {
        PlanStep step = step("step-1", "createOrder", Map.of("skuId", 10, "quantity", 1));
        String token = service.issue(SESSION_ID, step);

        assertThat(token).contains(".");
        assertThat(service.verifyAndConsume(SESSION_ID, step, token)).isTrue();
        // 重放：同一凭据第二次必须被拒
        assertThat(service.verifyAndConsume(SESSION_ID, step, token)).isFalse();
    }

    @Test
    void 篡改载荷后签名校验失败() throws Exception {
        PlanStep step = step("step-1", "createOrder", Map.of("skuId", 10));
        String token = service.issue(SESSION_ID, step);

        String body = token.substring(0, token.indexOf('.'));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sid", SESSION_ID);
        payload.put("step", "step-1");
        payload.put("tool", "cancelOrder");
        payload.put("op", "tampered");
        payload.put("exp", System.currentTimeMillis() / 1000 + 300);
        String tampered = encode(payload) + token.substring(token.indexOf('.'));

        assertThat(service.verifyAndConsume(SESSION_ID, step, tampered)).isFalse();
        assertThat(body).isNotEmpty();
    }

    @Test
    void 跨会话使用凭据被拒() {
        PlanStep step = step("step-1", "createOrder", Map.of("skuId", 10));
        String token = service.issue("sess-A", step);

        assertThat(service.verifyAndConsume("sess-B", step, token)).isFalse();
    }

    @Test
    void 同stepId同工具但操作内容不同_凭据被拒() {
        String token = service.issue(SESSION_ID, step("step-1", "createOrder", Map.of("skuId", 10)));
        // 确认的是 skuId=10，实际要执行 skuId=999，必须拒绝
        PlanStep another = step("step-1", "createOrder", Map.of("skuId", 999));

        assertThat(service.verifyAndConsume(SESSION_ID, another, token)).isFalse();
    }

    @Test
    void 已过期凭据被拒() throws Exception {
        PlanStep step = step("step-1", "createOrder", Map.of("skuId", 10));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sid", SESSION_ID);
        payload.put("step", "step-1");
        payload.put("tool", "createOrder");
        payload.put("op", null);
        payload.put("exp", System.currentTimeMillis() / 1000 - 1);

        String body = encode(payload);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String token = body + "."
                + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        assertThat(service.verifyAndConsume(SESSION_ID, step, token)).isFalse();
    }

    @Test
    void 非法格式凭据被拒() {
        PlanStep step = step("step-1", "createOrder", Map.of("skuId", 10));

        assertThat(service.verifyAndConsume(SESSION_ID, step, null)).isFalse();
        assertThat(service.verifyAndConsume(SESSION_ID, step, "  ")).isFalse();
        assertThat(service.verifyAndConsume(SESSION_ID, step, "no-dot-token")).isFalse();
        assertThat(service.verifyAndConsume(SESSION_ID, step, "!!!.###")).isFalse();
        assertThat(service.verifyAndConsume(SESSION_ID, step, "abc.")).isFalse();
    }

    private String encode(Map<String, Object> payload) throws Exception {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(payload));
    }
}
