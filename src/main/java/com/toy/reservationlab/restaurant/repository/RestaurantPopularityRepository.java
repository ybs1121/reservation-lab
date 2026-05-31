package com.toy.reservationlab.restaurant.repository;

import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.restaurant.dto.PopularRestaurantResponse;
import com.toy.reservationlab.restaurant.entity.PopularityPeriodType;
import com.toy.reservationlab.restaurant.entity.RestaurantPopularity;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantPopularityRepository extends JpaRepository<RestaurantPopularity, String> {

    @Modifying
    void deleteByPeriodTypeIn(Collection<PopularityPeriodType> periodTypes);

    /*
     * Batch Reader가 원본 예약 데이터에서 읽는 집계 대상이다.
     * 아직 집계 테이블을 보지 않고, Restaurant/Slot/Reservation을 기준으로 기간별 예약 건수를 만든다.
     */
    @Query("""
            SELECT new com.toy.reservationlab.restaurant.repository.PopularRestaurantAggregationRow(
                restaurant.restaurantId,
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
              AND (:startedAt IS NULL OR reservation.createdAt >= :startedAt)
              AND reservation.createdAt <= :endedAt
            GROUP BY restaurant.restaurantId, restaurant.createdAt
            ORDER BY COUNT(reservation) DESC, restaurant.createdAt ASC, restaurant.restaurantId ASC
            """)
    List<PopularRestaurantAggregationRow> findAggregationRows(
            @Param("restaurantStatus") RestaurantStatus restaurantStatus,
            @Param("reservationStatuses") Collection<ReservationStatus> reservationStatuses,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedAt") LocalDateTime endedAt
    );

    /*
     * API는 원본 예약 데이터를 다시 집계하지 않고 Batch가 저장한 읽기 모델을 조회한다.
     * 동률이면 Restaurant 등록이 빠른 순서로 고정해 응답 순서를 안정화한다.
     */
    @Query("""
            SELECT new com.toy.reservationlab.restaurant.dto.PopularRestaurantResponse(
                restaurant.restaurantId,
                restaurant.name,
                restaurant.address,
                restaurant.status,
                restaurant.delYn,
                popularity.reservationCount
            )
            FROM RestaurantPopularity popularity
            JOIN Restaurant restaurant ON restaurant.restaurantId = popularity.restaurantId
            WHERE popularity.periodType = :periodType
              AND restaurant.status = :restaurantStatus
              AND restaurant.delYn = 'N'
            ORDER BY popularity.reservationCount DESC, restaurant.createdAt ASC, restaurant.restaurantId ASC
            """)
    List<PopularRestaurantResponse> findPopularRestaurantsByPeriod(
            @Param("periodType") PopularityPeriodType periodType,
            @Param("restaurantStatus") RestaurantStatus restaurantStatus,
            Pageable pageable
    );
}
