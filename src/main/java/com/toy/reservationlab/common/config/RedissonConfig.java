package com.toy.reservationlab.common.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(DataRedisProperties.class)
@ConditionalOnProperty(name = "reservation-lab.distributed-lock.enabled", havingValue = "true")
public class RedissonConfig {

    private final DataRedisProperties redisProperties;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Duration timeout = redisProperties.getTimeout() == null
                ? Duration.ofSeconds(3)
                : redisProperties.getTimeout();
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setTimeout((int) timeout.toMillis());
        return Redisson.create(config);
    }
}
