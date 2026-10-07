package com.toy.reservationlab.common.outbox;

public record OutboxEventSnapshot(
        String eventId,
        String eventType,
        String payload,
        int retryCount
) {

    public static OutboxEventSnapshot from(OutboxEvent event) {
        return new OutboxEventSnapshot(
                event.getEventId(),
                event.getEventType(),
                event.getPayload(),
                event.getRetryCount()
        );
    }
}
