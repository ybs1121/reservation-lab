package com.toy.reservationlab.restaurant.service;

import com.toy.reservationlab.restaurant.dto.PopularRestaurantsResponse;
import com.toy.reservationlab.restaurant.entity.PopularityPeriodType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PopularRestaurantService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final PopularRestaurantCacheService popularRestaurantCacheService;

    public PopularRestaurantsResponse getPopularRestaurants(Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        return new PopularRestaurantsResponse(
                popularRestaurantCacheService.getPopularRestaurants(PopularityPeriodType.ALL_TIME, normalizedLimit),
                popularRestaurantCacheService.getPopularRestaurants(PopularityPeriodType.LAST_7_DAYS, normalizedLimit),
                popularRestaurantCacheService.getPopularRestaurants(PopularityPeriodType.LAST_30_DAYS, normalizedLimit),
                popularRestaurantCacheService.getPopularRestaurants(PopularityPeriodType.LAST_90_DAYS, normalizedLimit)
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
