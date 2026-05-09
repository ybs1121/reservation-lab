package com.toy.reservationlab.reservation.repository;

import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    @Query(value = """
            SELECT COUNT(*)
            FROM reservation r
            JOIN reservation_slot s ON r.slot_id = s.slot_id
            WHERE s.restaurant_id = :restaurantId
              AND s.slot_date > :today
              AND r.status = 'CONFIRMED'
              AND r.del_yn = 'N'
              AND s.del_yn = 'N'
            """, nativeQuery = true)
    long countFutureConfirmedReservation(
            @Param("restaurantId") String restaurantId,
            @Param("today") LocalDate today
    );

    long countBySlotIdAndStatusAndDelYn(String slotId, ReservationStatus status, String delYn);

    List<Reservation> findByUserIdAndStatusAndDelYn(String userId, ReservationStatus status, String delYn);
}
