package com.toy.reservationlab.reservation.dto;

import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;

public record ReservationResponse(
        String reservationId,
        String slotId,
        String userId,
        int partySize,
        ReservationStatus status,
        String delYn
) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getSlotId(),
                reservation.getUserId(),
                reservation.getPartySize(),
                reservation.getStatus(),
                reservation.getDelYn()
        );
    }
}
