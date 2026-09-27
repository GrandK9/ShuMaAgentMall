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
 * 各服务共用的 OpenAPI（Swagger）文档配置。
 * <p>
 * 放在 common 里靠 {@code scanBasePackages = "com.shumamall"} 被各服务扫到，
 * 避免 12 个服务各写一份内容雷同的配置类。
 * <p>
 * {@code @ConditionalOnClass(OpenAPI)} 是必需的：gateway 是 WebFlux 应用且未引入
 * springdoc（webmvc-ui 与 WebFlux 互斥），没有这个条件，Spring 扫到本类时会因
 * 找不到 {@code io.swagger.v3.oas.models.OpenAPI} 直接启动失败。
 * <p>
 * 访问地址：{@code http://localhost:<port>/swagger-ui.html}，
 * 原始文档：{@code http://localhost:<port>/v3/api-docs}。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfig {

    /** 与下方 SecurityScheme 名称一致，前端/调试时在 Authorize 里填 Bearer Token */
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
                                1) 先调用 shumamall-auth 的登录接口拿到 accessToken；
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
