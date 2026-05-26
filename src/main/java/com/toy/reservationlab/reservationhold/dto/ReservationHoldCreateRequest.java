package com.toy.reservationlab.reservationhold.dto;

public record ReservationHoldCreateRequest(
        String slotId,
        String userId,
        int partySize
) {
}
