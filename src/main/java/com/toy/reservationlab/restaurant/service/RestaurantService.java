package com.toy.reservationlab.restaurant.service;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.restaurant.entity.Restaurant;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {

    private static final String RESTAURANT_NOT_FOUND = "RST00001";

    private final RestaurantRepository restaurantRepository;

    @Transactional
    public Restaurant createRestaurant(
            String restaurantId,
            String name,
            String address,
            RestaurantStatus status,
            String createdBy
    ) {
        Restaurant restaurant = Restaurant.create(restaurantId, name, address, status, createdBy);
        return restaurantRepository.save(restaurant);
    }

    public Restaurant getRestaurant(String restaurantId) {
        return findRestaurant(restaurantId);
    }

    public boolean canCreateReservationSlot(String restaurantId) {
        return findRestaurant(restaurantId).canCreateReservationSlot();
    }

    private Restaurant findRestaurant(String restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BizException(RESTAURANT_NOT_FOUND));
    }
}

