package com.toy.reservationlab.reservationhold.component;

public record ReservationHoldRequestedPayload(
        String requestId,
        String slotId,
        String userId,
        int partySize
) {
}
