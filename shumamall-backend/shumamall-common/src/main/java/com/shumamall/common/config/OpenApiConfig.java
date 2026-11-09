package com.shumamall.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 各 WebMVC 服务共用的 OpenAPI（Swagger）文档配置。
 * <p>
 * 随 {@code scanBasePackages} 包含 {@code com.shumamall.common} 的进程自动生效，
 * 各服务只需引入 springdoc starter，不必重复写 JWT Bearer 等说明。
 * <p>
 * {@code @ConditionalOnClass(OpenAPI)}：网关为 WebFlux 且不引 springdoc，避免扫到本类后缺类启动失败。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Value("${spring.application.name:shumamall}")
    private String applicationName;

    @Bean
    public OpenAPI shumamallOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("数码商城 " + applicationName + " API")
                        .version("1.0.0")
                        .description("""
                                数码商城微服务接口文档。写操作（POST/PUT/PATCH/DELETE）经网关时需要：
                                1) 先调用 shumamall-iam（/api/v1/auth/login）拿到 accessToken；
                                2) 点右上角 Authorize 填入该 token；
                                3) 写请求还需携带 X-Timestamp / X-Nonce / X-Sign 三个签名头，
                                   仅靠 Swagger UI 无法自动生成签名，建议用接口调试脚本验证写操作。
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 accessToken")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
