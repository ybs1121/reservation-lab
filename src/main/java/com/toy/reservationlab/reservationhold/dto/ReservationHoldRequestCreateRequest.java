package com.toy.reservationlab.reservationhold.dto;

public record ReservationHoldRequestCreateRequest(
        String slotId,
        String userId,
        int partySize
) {
}
