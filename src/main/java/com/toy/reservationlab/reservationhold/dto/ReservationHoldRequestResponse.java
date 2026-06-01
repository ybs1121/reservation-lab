package com.toy.reservationlab.reservationhold.dto;

import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequest;
import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequestStatus;

public record ReservationHoldRequestResponse(
        String requestId,
        ReservationHoldRequestStatus status,
        String holdId,
        String failureCode,
        String failureMessage
) {

    public static ReservationHoldRequestResponse from(ReservationHoldRequest request) {
        return new ReservationHoldRequestResponse(
                request.getRequestId(),
                request.getStatus(),
                request.getHoldId(),
                request.getFailureCode(),
                request.getFailureMessage()
        );
    }
}
