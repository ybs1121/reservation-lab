package com.toy.reservationlab.reservation.dto;

public record ReservationCreateRequest(
        String reservationId,
        String slotId,
        String userId,
        int partySize,
        String createdBy
) {
}
