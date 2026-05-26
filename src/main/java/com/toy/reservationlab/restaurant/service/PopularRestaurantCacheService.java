package com.toy.reservationlab.restaurant.service;

import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.restaurant.component.PopularRestaurantCacheNames;
import com.toy.reservationlab.restaurant.dto.PopularRestaurantResponse;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.repository.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopularRestaurantCacheService {

    private static final List<ReservationStatus> POPULAR_TARGET_STATUSES = List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.NO_SHOW
    );

    private final RestaurantRepository restaurantRepository;

    // 전체 기간 인기는 4단계 배치 전까지 실시간 예약 건수 집계 결과를 캐시한다.
    @Cacheable(cacheNames = PopularRestaurantCacheNames.ALL_TIME, key = "#limit")
    public List<PopularRestaurantResponse> getAllTimePopularRestaurants(int limit) {
        return restaurantRepository.findAllTimePopularRestaurants(
                RestaurantStatus.OPEN,
                POPULAR_TARGET_STATUSES,
                PageRequest.of(0, limit)
        );
    }

    // 최근 인기는 요청 recentDays별로 캐시 key를 분리해 사용자가 두 기준을 비교할 수 있게 한다.
    @Cacheable(cacheNames = PopularRestaurantCacheNames.RECENT, key = "#recentDays + ':' + #limit")
    public List<PopularRestaurantResponse> getRecentPopularRestaurants(int recentDays, int limit) {
        return restaurantRepository.findRecentPopularRestaurants(
                RestaurantStatus.OPEN,
                POPULAR_TARGET_STATUSES,
                LocalDateTime.now().minusDays(recentDays),
                PageRequest.of(0, limit)
        );
    }
}
