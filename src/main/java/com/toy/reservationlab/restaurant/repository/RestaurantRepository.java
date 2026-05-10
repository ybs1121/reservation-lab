package com.toy.reservationlab.restaurant.repository;

import com.toy.reservationlab.restaurant.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, String> {
}

