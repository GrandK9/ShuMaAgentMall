package com.shumamall.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 客户端 IP 透传过滤器。
 * <p>
 * 网关是唯一入口，也是信任边界：把 TCP 连接的远端地址**覆盖**写入
 * {@code X-Forwarded-For}，下游服务（如 {@code @RequirePermission} 切面的操作审计）
 * 才能拿到可信的客户端 IP。
 * <p>
 * <b>覆盖而不是追加：</b>该请求头客户端可以随意伪造，若采取「保留已有值再追加」的常规写法，
 * 下游取第一段拿到的仍是伪造值；这里直接 {@code set} 覆盖，客户端带什么都会被丢弃。
 * <p>
 * 不去读 XFF 的原因同 {@code RateLimitConfig.resolveClientIp}：限流键与审计记录
 * 都必须建立在不可伪造的字段上。
 */
@Component
public class ClientIpForwardFilter implements GlobalFilter, Ordered {

    /** 与业务侧（PermissionAspect）读取的请求头保持一致 */
    public static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            // 取不到远端地址（如某些测试环境）时不改请求头，下游退化为记录自身看到的对端地址
            return chain.filter(exchange);
        }

        String clientIp = remoteAddress.getAddress().getHostAddress();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER_FORWARDED_FOR, clientIp))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * 必须早于路由转发（{@code NettyRoutingFilter}）执行，否则头改了也不会被发出；
     * 也用最高优先级避免与同为 {@code LOWEST_PRECEDENCE} 的过滤器产生顺序歧义。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
