package com.toy.reservationlab.reservationslot.repository;

import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, String> {

    boolean existsByRestaurantIdAndSlotDateAndSlotTimeAndDelYn(
            String restaurantId,
            LocalDate slotDate,
            String slotTime,
            String delYn
    );

    @Query("""
            SELECT COUNT(s)
            FROM ReservationSlot s
            WHERE s.restaurantId = :restaurantId
              AND s.slotDate = :slotDate
              AND s.slotTime = :slotTime
              AND s.delYn = 'N'
              AND s.slotId <> :slotId
            """)
    long countActiveDuplicateSlot(
            @Param("restaurantId") String restaurantId,
            @Param("slotDate") LocalDate slotDate,
            @Param("slotTime") String slotTime,
            @Param("slotId") String slotId
    );
}
