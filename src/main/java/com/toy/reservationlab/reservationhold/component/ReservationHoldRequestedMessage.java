package com.toy.reservationlab.reservationhold.component;

public record ReservationHoldRequestedMessage(
        String messageId,
        String eventType,
        int payloadVersion,
        String occurredAt,
        ReservationHoldRequestedPayload payload
) {
}
