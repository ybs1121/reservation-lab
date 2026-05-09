package com.toy.reservationlab.reservationslot.dto;

import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import java.time.LocalDate;

public record ReservationSlotResponse(
        String slotId,
        String restaurantId,
        LocalDate slotDate,
        String slotTime,
        int capacity,
        ReservationSlotStatus status,
        String delYn
) {

    public static ReservationSlotResponse from(ReservationSlot slot) {
        return new ReservationSlotResponse(
                slot.getSlotId(),
                slot.getRestaurantId(),
                slot.getSlotDate(),
                slot.getSlotTime(),
                slot.getCapacity(),
                slot.getStatus(),
                slot.getDelYn()
        );
    }
}
