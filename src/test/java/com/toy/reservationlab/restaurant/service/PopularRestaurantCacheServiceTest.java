package com.toy.reservationlab.restaurant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.toy.reservationlab.restaurant.component.PopularRestaurantCacheEvictor;
import com.toy.reservationlab.restaurant.dto.PopularRestaurantsResponse;
import com.toy.reservationlab.restaurant.entity.PopularityPeriodType;
import com.toy.reservationlab.restaurant.entity.RestaurantPopularity;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.repository.RestaurantPopularityRepository;
import com.toy.reservationlab.restaurant.repository.RestaurantRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "reservation-lab.popular-restaurant-cache.enabled=true"
})
class PopularRestaurantCacheServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private PopularRestaurantService popularRestaurantService;

    @Autowired
    private PopularRestaurantCacheEvictor popularRestaurantCacheEvictor;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private RestaurantPopularityRepository restaurantPopularityRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @BeforeEach
    void setUp() {
        popularRestaurantCacheEvictor.evictAll();
        restaurantPopularityRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void popularRestaurants_returnsAllBatchPeriods() {
        createRestaurant("popular-all-time-restaurant", "All Time Restaurant");
        createRestaurant("popular-recent-restaurant", "Recent Restaurant");
        savePopularity("popular-all-time-row", "popular-all-time-restaurant", PopularityPeriodType.ALL_TIME, 2);
        savePopularity("popular-last-7-row", "popular-recent-restaurant", PopularityPeriodType.LAST_7_DAYS, 1);
        savePopularity("popular-last-30-row", "popular-recent-restaurant", PopularityPeriodType.LAST_30_DAYS, 1);
        savePopularity("popular-last-90-row", "popular-recent-restaurant", PopularityPeriodType.LAST_90_DAYS, 1);

        PopularRestaurantsResponse response = popularRestaurantService.getPopularRestaurants(10);

        assertEquals("popular-all-time-restaurant", response.allTime().getFirst().restaurantId());
        assertEquals(2, response.allTime().getFirst().reservationCount());
        assertEquals("popular-recent-restaurant", response.last7Days().getFirst().restaurantId());
        assertEquals("popular-recent-restaurant", response.last30Days().getFirst().restaurantId());
        assertEquals("popular-recent-restaurant", response.last90Days().getFirst().restaurantId());
    }

    @Test
    void sameCondition_returnsRedisCachedResponse() {
        createRestaurant("popular-cache-old-restaurant", "Old Popular Restaurant");
        createRestaurant("popular-cache-new-restaurant", "New Popular Restaurant");
        savePopularity("popular-cache-old-row", "popular-cache-old-restaurant", PopularityPeriodType.ALL_TIME, 1);

        PopularRestaurantsResponse first = popularRestaurantService.getPopularRestaurants(10);
        savePopularity("popular-cache-new-row", "popular-cache-new-restaurant", PopularityPeriodType.ALL_TIME, 2);

        PopularRestaurantsResponse second = popularRestaurantService.getPopularRestaurants(10);
        popularRestaurantCacheEvictor.evictAll();
        PopularRestaurantsResponse afterEvict = popularRestaurantService.getPopularRestaurants(10);

        assertEquals("popular-cache-old-restaurant", first.allTime().getFirst().restaurantId());
        assertEquals("popular-cache-old-restaurant", second.allTime().getFirst().restaurantId());
        assertEquals("popular-cache-new-restaurant", afterEvict.allTime().getFirst().restaurantId());
    }

    @Test
    void evictAll_refreshesPeriodCache() {
        createRestaurant("popular-evict-old-restaurant", "Old Recent Restaurant");
        createRestaurant("popular-evict-new-restaurant", "New Recent Restaurant");
        savePopularity("popular-evict-old-row", "popular-evict-old-restaurant", PopularityPeriodType.LAST_7_DAYS, 1);
        popularRestaurantService.getPopularRestaurants(10);

        savePopularity("popular-evict-new-row", "popular-evict-new-restaurant", PopularityPeriodType.LAST_7_DAYS, 2);
        popularRestaurantCacheEvictor.evictAll();
        PopularRestaurantsResponse response = popularRestaurantService.getPopularRestaurants(10);

        assertEquals("popular-evict-new-restaurant", response.last7Days().getFirst().restaurantId());
        assertEquals(2, response.last7Days().getFirst().reservationCount());
    }

    @Test
    void restaurantUpdate_evictsPopularRestaurantCache() {
        createRestaurant("popular-update-restaurant", "Before Update");
        savePopularity("popular-update-row", "popular-update-restaurant", PopularityPeriodType.ALL_TIME, 1);
        popularRestaurantService.getPopularRestaurants(10);

        restaurantService.updateRestaurant(
                "popular-update-restaurant",
                "After Update",
                "Seoul Mapo",
                RestaurantStatus.OPEN,
                "system"
        );
        PopularRestaurantsResponse response = popularRestaurantService.getPopularRestaurants(10);

        assertEquals("After Update", response.allTime().getFirst().name());
    }

    private void createRestaurant(String restaurantId, String name) {
        restaurantService.createRestaurant(
                restaurantId,
                name,
                "Seoul Gangnam",
                RestaurantStatus.OPEN,
                "system"
        );
    }

    private void savePopularity(
            String popularityId,
            String restaurantId,
            PopularityPeriodType periodType,
            long reservationCount
    ) {
        restaurantPopularityRepository.save(RestaurantPopularity.create(
                popularityId,
                restaurantId,
                periodType,
                reservationCount,
                LocalDateTime.now(),
                "system"
        ));
    }
}
