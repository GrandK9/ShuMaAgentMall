package com.shumamall.gateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 秒杀路由的限流豁免。
 * <p>
 * 全局令牌桶配在 {@code default-filters} 上，对<b>所有</b>路由生效，而 Spring Cloud Gateway
 * 不支持按路由摘掉 default-filter（路由级 {@code filters} 是追加而不是覆盖）。可秒杀恰恰是
 * 唯一需要「放开入口、把削峰下沉到业务侧」的场景：拿 20 请求/秒的用户维度令牌桶去卡抢购，
 * 结果是绝大多数正常用户直接吃 429，而超卖与否本来也不由网关决定。
 * <p>
 * 因此这里接管 {@link RateLimiter}：命中豁免路由直接放行，其余路由仍走原来的 Redis 令牌桶。
 * 放行不等于裸奔 —— 秒杀链路自身有 Redis Lua 原子预扣做「一人一单 + 库存」两层硬约束，
 * 溢出的无效流量在 Lua 那一层就被挡回，不会打到数据库。
 */
@Configuration
public class SeckillRateLimitConfig {

    /**
     * 豁免全局令牌桶的路由 id。
     * <p>
     * 与 {@code bootstrap.yml} 中的 {@code seckill-service} 一一对应，改名需同步。
     * 只豁免用户端抢购入口：管理端的 {@code admin-seckill-service} 是低频后台操作，照旧限流。
     */
    private static final Set<String> EXEMPT_ROUTE_IDS = Set.of("seckill-service");

    /**
     * 接管 {@link RateLimiter}：命中豁免路由直接放行，其余委托给标准 Redis 令牌桶。
     * <p>
     * 标 {@code @Primary} 是因为容器里同时存在 SCG 自动配置的 {@code redisRateLimiter}，
     * 而 {@code RequestRateLimiterGatewayFilterFactory} 按 {@link RateLimiter} 类型注入。
     *
     * @param redisTemplate        响应式 Redis 模板（令牌桶存储）
     * @param redisScript          SCG 自带的令牌桶 Lua 脚本
     * @param configurationService 路由级限流参数解析（读取 default-filters 里的 args）
     * @return 带路由豁免的限流器
     */
    @Bean
    @Primary
    public RedisRateLimiter seckillAwareRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            @Qualifier("redisRequestRateLimiterScript") RedisScript<List<Long>> redisScript,
            ConfigurationService configurationService) {
        return new ExemptingRedisRateLimiter(redisTemplate, redisScript, configurationService);
    }

    /**
     * 带路由豁免的 Redis 令牌桶：继承标准实现，只在 {@code isAllowed} 的入口处短路。
     */
    private static class ExemptingRedisRateLimiter extends RedisRateLimiter {

        ExemptingRedisRateLimiter(ReactiveStringRedisTemplate redisTemplate,
                                  RedisScript<List<Long>> redisScript,
                                  ConfigurationService configurationService) {
            super(redisTemplate, redisScript, configurationService);
        }

        @Override
        public Mono<Response> isAllowed(String routeId, String id) {
            if (EXEMPT_ROUTE_IDS.contains(routeId)) {
                return Mono.just(new Response(true, Collections.emptyMap()));
            }
            return super.isAllowed(routeId, id);
        }
    }
}
