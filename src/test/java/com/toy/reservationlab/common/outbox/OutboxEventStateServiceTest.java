package com.toy.reservationlab.common.outbox;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventStateServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void 첫_발행_실패는_1초_뒤_READY_재시도로_변경한다() {
        OutboxEventStateService service = new OutboxEventStateService(outboxEventRepository);
        LocalDateTime failedAt = LocalDateTime.of(2026, 7, 19, 14, 0);
        OutboxEventSnapshot event = event(0);

        service.markPublishFailed(event, "claim-1", failedAt, "connection failed");

        verify(outboxEventRepository).markPublishFailed(
                eq(event.eventId()),
                eq("claim-1"),
                eq(OutboxEventStatus.READY),
                eq(1),
                eq(failedAt.plusSeconds(1)),
                eq("connection failed"),
                eq(failedAt),
                eq(OutboxEventStatus.SENDING)
        );
    }

    @Test
    void 다섯_번째_발행_실패는_DEAD로_변경한다() {
        OutboxEventStateService service = new OutboxEventStateService(outboxEventRepository);
        LocalDateTime failedAt = LocalDateTime.of(2026, 7, 19, 14, 0);
        OutboxEventSnapshot event = event(4);

        service.markPublishFailed(event, "claim-1", failedAt, "publisher nack");

        verify(outboxEventRepository).markPublishFailed(
                eq(event.eventId()),
                eq("claim-1"),
                eq(OutboxEventStatus.DEAD),
                eq(5),
                eq(failedAt),
                eq("publisher nack"),
                eq(failedAt),
                eq(OutboxEventStatus.SENDING)
        );
    }

    private OutboxEventSnapshot event(int retryCount) {
        return new OutboxEventSnapshot(
                "event-1",
                "RESERVATION_HOLD_REQUESTED",
                "{}",
                retryCount
        );
    }
}
