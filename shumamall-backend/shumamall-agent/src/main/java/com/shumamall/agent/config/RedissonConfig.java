package com.shumamall.agent.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 客户端。
 * <p>
 * 只给耗时不确定的互斥（全量重建）使用：不指定租期时由看门狗续期，进程退出后续期停止，锁会自动释放。
 * 短占位（消费去重、确认令牌）仍走 Spring Data Redis 的 SET NX EX，不经过这里。
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database) {
        Config config = new Config();
        var server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database);
        if (StringUtils.hasText(password)) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}
