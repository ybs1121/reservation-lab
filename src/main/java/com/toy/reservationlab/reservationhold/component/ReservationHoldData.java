package com.toy.reservationlab.reservationhold.component;

public record ReservationHoldData(
        String holdId,
        String slotId,
        String userId,
        int partySize
) {
}
