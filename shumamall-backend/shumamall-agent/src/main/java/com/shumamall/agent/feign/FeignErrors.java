package com.shumamall.agent.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;

/**
 * 还原 Feign 调用的失败原因。
 * <p>
 * 开启 Spring Cloud CircuitBreaker 后，下游返回 4xx/5xx 时 Feign 抛出的 {@code FeignException}
 * 会被 Resilience4j 包进 {@code NoFallbackAvailableException}（未配置 fallback）。
 * 此时直接取 {@code e.getMessage()} 只会拿到 "No fallback available."，
 * 下游真正的业务码与提示（如「缺少请求签名头」）全部丢失——排查时看不到原因，
 * LLM 也没法把失败原因转述给用户。
 * <p>
 * 与 payment 服务处理订单服务失败的方式保持一致：沿 cause 链解包，再从响应体里取业务 {@code msg}。
 */
public final class FeignErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FeignErrors() {
    }

    /**
     * 从异常原因链里找出 {@link FeignException}。
     *
     * @param throwable 捕获到的异常，可为 {@code null}
     * @return 原因链中的 FeignException；不存在时返回 {@code null}
     */
    public static FeignException find(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof FeignException feignException) {
                return feignException;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    /**
     * 生成面向调用方（LLM / 用户）的失败原因描述。
     * <p>
     * 优先取下游响应体里的业务提示（最贴近用户视角的一句话），
     * 取不到则退回 FeignException 自身的描述，再退回原始异常信息。
     *
     * @param throwable 捕获到的异常
     * @return 非空的失败原因
     */
    public static String message(Throwable throwable) {
        FeignException feignException = find(throwable);
        if (feignException == null) {
            String raw = throwable == null ? null : throwable.getMessage();
            return (raw == null || raw.isBlank()) ? "未知错误" : raw;
        }
        String businessMessage = businessMessage(feignException);
        if (businessMessage != null) {
            return businessMessage;
        }
        String raw = feignException.getMessage();
        return (raw == null || raw.isBlank()) ? "下游服务调用失败" : raw;
    }

    /**
     * 解析下游响应体里的业务提示 {@code msg}。
     *
     * @param e 携带原始响应体的 Feign 异常
     * @return 业务提示；响应体不是统一返回结构或没有提示时返回 {@code null}
     */
    private static String businessMessage(FeignException e) {
        try {
            JsonNode node = MAPPER.readTree(e.contentUTF8());
            if (node == null) {
                return null;
            }
            String msg = node.path("msg").asText("");
            return msg.isBlank() ? null : msg;
        } catch (Exception ignore) {
            return null;
        }
    }
}
