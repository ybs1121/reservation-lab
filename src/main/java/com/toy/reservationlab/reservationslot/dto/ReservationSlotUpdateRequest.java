package com.toy.reservationlab.reservationslot.dto;

import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import java.time.LocalDate;

public record ReservationSlotUpdateRequest(
        String restaurantId,
        LocalDate slotDate,
        String slotTime,
        int capacity,
        ReservationSlotStatus status,
        String updatedBy
) {
}
