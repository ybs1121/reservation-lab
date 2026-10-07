package com.toy.reservationlab.common.outbox;

import com.toy.reservationlab.reservationhold.component.ReservationHoldRequestPublisher;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("""
        '${reservation-lab.reservation-hold-request.enabled:false}' == 'true'
        && '${reservation-lab.outbox-publisher.enabled:false}' == 'true'
        """)
public class OutboxPublisher {

    private static final String RESERVATION_HOLD_REQUESTED = "RESERVATION_HOLD_REQUESTED";

    private final OutboxEventStateService outboxEventStateService;
    private final ReservationHoldRequestPublisher reservationHoldRequestPublisher;

    @Value("${reservation-lab.outbox-publisher.batch-size:100}")
    private int batchSize;

    @Value("${reservation-lab.outbox-publisher.stale-timeout-seconds:30}")
    private long staleTimeoutSeconds;

    @Scheduled(fixedDelayString = "${reservation-lab.outbox-publisher.polling-delay-millis:1000}")
    public void publishReadyEvents() {
        LocalDateTime now = LocalDateTime.now();
        outboxEventStateService.recoverStaleSending(now.minusSeconds(staleTimeoutSeconds), now);
        for (OutboxEventSnapshot event : outboxEventStateService.findReadyEvents(now, batchSize)) {
            publish(event);
        }
    }

    private void publish(OutboxEventSnapshot event) {
        String claimId = UUID.randomUUID().toString();
        LocalDateTime claimedAt = LocalDateTime.now();
        if (!outboxEventStateService.claim(event.eventId(), claimId, claimedAt)) {
            return;
        }

        try {
            publishByEventType(event);
            outboxEventStateService.markPublished(event.eventId(), claimId, LocalDateTime.now());
        } catch (RuntimeException exception) {
            outboxEventStateService.markPublishFailed(
                    event,
                    claimId,
                    LocalDateTime.now(),
                    exception.getMessage()
            );
        }
    }

    private void publishByEventType(OutboxEventSnapshot event) {
        if (!RESERVATION_HOLD_REQUESTED.equals(event.eventType())) {
            throw new IllegalArgumentException("Unsupported outbox event type: " + event.eventType());
        }
        reservationHoldRequestPublisher.publish(event.eventId(), event.payload());
    }
}
