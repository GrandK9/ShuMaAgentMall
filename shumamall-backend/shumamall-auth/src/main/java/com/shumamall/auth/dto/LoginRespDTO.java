package com.shumamall.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRespDTO {

    private String token;

    /**
     * 请求签名密钥。
     * <p>
     * 由 JWT 密钥 + 本次 token 的 {@code userId/iat} 派生（见 {@code SignSecretResolver}），
     * 客户端保存于内存/sessionStorage，调用下单、支付等写接口时用它算 X-Sign。
     * 与 token 同生命周期：重新登录会拿到新 token，密钥随之变化，旧密钥自动失效。
     */
    private String signSecret;

    private Long userId;
    private String username;
    private List<String> roleCodes;
}
