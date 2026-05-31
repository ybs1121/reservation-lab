package com.toy.reservationlab.restaurant.dto;

import java.util.List;

public record PopularRestaurantsResponse(
        List<PopularRestaurantResponse> allTime,
        List<PopularRestaurantResponse> last7Days,
        List<PopularRestaurantResponse> last30Days,
        List<PopularRestaurantResponse> last90Days
) {
}
