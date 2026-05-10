package com.toy.reservationlab.reservation.dto;

import com.toy.reservationlab.reservation.entity.ReservationStatus;

public record ReservationUpdateRequest(
        ReservationStatus status,
        String updatedBy
) {
}
