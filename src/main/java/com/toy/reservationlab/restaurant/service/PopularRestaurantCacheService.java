package com.toy.reservationlab.restaurant.service;

import com.toy.reservationlab.restaurant.component.PopularRestaurantCacheNames;
import com.toy.reservationlab.restaurant.dto.PopularRestaurantResponse;
import com.toy.reservationlab.restaurant.entity.PopularityPeriodType;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.repository.RestaurantPopularityRepository;
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

    private final RestaurantPopularityRepository restaurantPopularityRepository;

    // API는 실시간 예약 테이블을 다시 세지 않고, Batch가 만든 기간별 read model을 캐시해서 조회한다.
    @Cacheable(cacheNames = PopularRestaurantCacheNames.PERIOD, key = "#periodType.name() + ':' + #limit")
    public List<PopularRestaurantResponse> getPopularRestaurants(PopularityPeriodType periodType, int limit) {
        return restaurantPopularityRepository.findPopularRestaurantsByPeriod(
                periodType,
                RestaurantStatus.OPEN,
                PageRequest.of(0, limit)
        );
    }
}
