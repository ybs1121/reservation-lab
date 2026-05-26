package com.toy.reservationlab.restaurant.component;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PopularRestaurantCacheEvictor {

    private static final List<String> CACHE_NAMES = List.of(
            PopularRestaurantCacheNames.ALL_TIME,
            PopularRestaurantCacheNames.RECENT
    );

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Value("${reservation-lab.popular-restaurant-cache.enabled:false}")
    private boolean cacheEnabled;

    // 인기 캐시 key가 limit/recentDays별로 나뉘어 있어, 4단계 집계 테이블 전까지는 전체 무효화한다.
    public void evictAll() {
        if (!cacheEnabled) {
            return;
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        for (String cacheName : CACHE_NAMES) {
            // 3단계는 전체 무효화 우선이다. 운영 규모에서는 KEYS 대신 scan/집계 캐시 교체로 바꾼다.
            Set<String> keys = redisTemplate.keys(cacheName + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }
}
