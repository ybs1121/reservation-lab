package com.toy.reservationlab.restaurant.repository;

import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.restaurant.dto.PopularRestaurantResponse;
import com.toy.reservationlab.restaurant.entity.Restaurant;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantRepository extends JpaRepository<Restaurant, String> {

    @Query("""
            SELECT new com.toy.reservationlab.restaurant.dto.PopularRestaurantResponse(
                restaurant.restaurantId,
                restaurant.name,
                restaurant.address,
                restaurant.status,
                restaurant.delYn,
                COUNT(reservation)
            )
            FROM Restaurant restaurant
            JOIN ReservationSlot slot ON slot.restaurantId = restaurant.restaurantId
            JOIN Reservation reservation ON reservation.slotId = slot.slotId
            WHERE restaurant.status = :restaurantStatus
              AND restaurant.delYn = 'N'
              AND slot.delYn = 'N'
              AND reservation.status IN :reservationStatuses
              AND reservation.delYn = 'N'
            GROUP BY restaurant.restaurantId, restaurant.name, restaurant.address, restaurant.status, restaurant.delYn
            ORDER BY COUNT(reservation) DESC, restaurant.restaurantId ASC
            """)
    List<PopularRestaurantResponse> findAllTimePopularRestaurants(
            @Param("restaurantStatus") RestaurantStatus restaurantStatus,
            @Param("reservationStatuses") Collection<ReservationStatus> reservationStatuses,
            Pageable pageable
    );

    @Query("""
            SELECT new com.toy.reservationlab.restaurant.dto.PopularRestaurantResponse(
                restaurant.restaurantId,
                restaurant.name,
                restaurant.address,
                restaurant.status,
                restaurant.delYn,
                COUNT(reservation)
            )
            FROM Restaurant restaurant
            JOIN ReservationSlot slot ON slot.restaurantId = restaurant.restaurantId
            JOIN Reservation reservation ON reservation.slotId = slot.slotId
            WHERE restaurant.status = :restaurantStatus
              AND restaurant.delYn = 'N'
              AND slot.delYn = 'N'
              AND reservation.status IN :reservationStatuses
              AND reservation.delYn = 'N'
              AND reservation.createdAt >= :startedAt
            GROUP BY restaurant.restaurantId, restaurant.name, restaurant.address, restaurant.status, restaurant.delYn
            ORDER BY COUNT(reservation) DESC, restaurant.restaurantId ASC
            """)
    List<PopularRestaurantResponse> findRecentPopularRestaurants(
            @Param("restaurantStatus") RestaurantStatus restaurantStatus,
            @Param("reservationStatuses") Collection<ReservationStatus> reservationStatuses,
            @Param("startedAt") LocalDateTime startedAt,
            Pageable pageable
    );
}
