package com.toy.reservationlab.reservationhold.dto;

public record ReservationHoldConfirmRequest(
        String reservationId,
        String userId,
        String createdBy
) {
}
