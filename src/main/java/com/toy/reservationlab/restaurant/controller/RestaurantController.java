package com.toy.reservationlab.restaurant.controller;

import com.toy.reservationlab.common.component.ApiResponse;
import com.toy.reservationlab.restaurant.dto.RestaurantCreateRequest;
import com.toy.reservationlab.restaurant.dto.RestaurantDeleteRequest;
import com.toy.reservationlab.restaurant.dto.RestaurantResponse;
import com.toy.reservationlab.restaurant.dto.RestaurantUpdateRequest;
import com.toy.reservationlab.restaurant.entity.Restaurant;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping
    public ApiResponse<RestaurantResponse> createRestaurant(@RequestBody RestaurantCreateRequest request) {
        Restaurant restaurant = restaurantService.createRestaurant(
                request.restaurantId(),
                request.name(),
                request.address(),
                request.status(),
                request.createdBy()
        );
        return ApiResponse.success(RestaurantResponse.from(restaurant));
    }

    @GetMapping("/{restaurantId}")
    public ApiResponse<RestaurantResponse> getRestaurant(@PathVariable String restaurantId) {
        Restaurant restaurant = restaurantService.getRestaurant(restaurantId);
        return ApiResponse.success(RestaurantResponse.from(restaurant));
    }

    @PutMapping("/{restaurantId}")
    public ApiResponse<RestaurantResponse> updateRestaurant(
            @PathVariable String restaurantId,
            @RequestBody RestaurantUpdateRequest request
    ) {
        Restaurant restaurant = restaurantService.updateRestaurant(
                restaurantId,
                request.name(),
                request.address(),
                request.status(),
                request.updatedBy()
        );
        return ApiResponse.success(RestaurantResponse.from(restaurant));
    }

    @DeleteMapping("/{restaurantId}")
    public ApiResponse<RestaurantResponse> deleteRestaurant(
            @PathVariable String restaurantId,
            @RequestBody RestaurantDeleteRequest request
    ) {
        Restaurant restaurant = restaurantService.deleteRestaurant(restaurantId, request.updatedBy());
        return ApiResponse.success(RestaurantResponse.from(restaurant));
    }
}
