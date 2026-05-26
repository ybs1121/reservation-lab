package com.toy.reservationlab.restaurant.dto;

import com.toy.reservationlab.restaurant.entity.RestaurantStatus;

public record PopularRestaurantResponse(
        String restaurantId,
        String name,
        String address,
        RestaurantStatus status,
        String delYn,
        long reservationCount
) {
}
