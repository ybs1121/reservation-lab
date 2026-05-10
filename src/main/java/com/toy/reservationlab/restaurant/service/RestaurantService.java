package com.toy.reservationlab.restaurant.service;

import static com.toy.reservationlab.common.component.ErrorCode.FUTURE_RESERVATION_EXISTS;
import static com.toy.reservationlab.common.component.ErrorCode.RESTAURANT_NOT_FOUND;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.restaurant.entity.Restaurant;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.repository.RestaurantRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final ReservationRepository reservationRepository;

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

    @Transactional
    public Restaurant updateRestaurant(
            String restaurantId,
            String name,
            String address,
            RestaurantStatus status,
            String updatedBy
    ) {
        Restaurant restaurant = findRestaurant(restaurantId);
        restaurant.update(name, address, status, updatedBy);
        return restaurant;
    }

    @Transactional
    public Restaurant deleteRestaurant(String restaurantId, String updatedBy) {
        Restaurant restaurant = findRestaurant(restaurantId);
        if (hasFutureConfirmedReservation(restaurantId)) {
            throw new BizException(FUTURE_RESERVATION_EXISTS);
        }
        restaurant.markDeleted(updatedBy);
        return restaurant;
    }

    public boolean canCreateReservationSlot(String restaurantId) {
        return findRestaurant(restaurantId).canCreateReservationSlot();
    }

    private boolean hasFutureConfirmedReservation(String restaurantId) {
        return reservationRepository.countFutureConfirmedReservation(restaurantId, LocalDate.now()) > 0;
    }

    private Restaurant findRestaurant(String restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BizException(RESTAURANT_NOT_FOUND));
    }
}
