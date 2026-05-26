package com.toy.reservationlab.restaurant.service;

import com.toy.reservationlab.restaurant.dto.PopularRestaurantsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PopularRestaurantService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_RECENT_DAYS = 7;

    private final PopularRestaurantCacheService popularRestaurantCacheService;

    public PopularRestaurantsResponse getPopularRestaurants(Integer limit, Integer recentDays) {
        int normalizedLimit = limit == null ? DEFAULT_LIMIT : limit;
        int normalizedRecentDays = recentDays == null ? DEFAULT_RECENT_DAYS : recentDays;
        return new PopularRestaurantsResponse(
                popularRestaurantCacheService.getAllTimePopularRestaurants(normalizedLimit),
                popularRestaurantCacheService.getRecentPopularRestaurants(normalizedRecentDays, normalizedLimit)
        );
    }
}
