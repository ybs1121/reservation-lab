package com.toy.reservationlab.restaurant.dto;

import com.toy.reservationlab.restaurant.entity.RestaurantStatus;

public record RestaurantUpdateRequest(
        String name,
        String address,
        RestaurantStatus status,
        String updatedBy
) {
}
