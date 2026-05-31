package com.toy.reservationlab.restaurant.repository;

public record PopularRestaurantAggregationRow(
        String restaurantId,
        long reservationCount
) {
}
