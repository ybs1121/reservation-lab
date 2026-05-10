package com.toy.reservationlab.restaurant.dto;

import com.toy.reservationlab.restaurant.entity.Restaurant;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;

public record RestaurantResponse(
        String restaurantId,
        String name,
        String address,
        RestaurantStatus status,
        String delYn
) {

    public static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getRestaurantId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getStatus(),
                restaurant.getDelYn()
        );
    }
}

