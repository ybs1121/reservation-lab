package com.toy.reservationlab.reservationhold.dto;

import com.toy.reservationlab.reservationhold.component.ReservationHoldData;

public record ReservationHoldResponse(
        String holdId,
        String slotId,
        String userId,
        int partySize,
        long ttlSeconds
) {

    public static ReservationHoldResponse from(ReservationHoldData hold, long ttlSeconds) {
        return new ReservationHoldResponse(
                hold.holdId(),
                hold.slotId(),
                hold.userId(),
                hold.partySize(),
                ttlSeconds
        );
    }
}
