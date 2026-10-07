package com.toy.reservationlab.common.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toy.reservationlab.reservationhold.component.ReservationHoldRequestPublisher;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventStateService outboxEventStateService;

    @Mock
    private ReservationHoldRequestPublisher reservationHoldRequestPublisher;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxEventStateService, reservationHoldRequestPublisher);
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 100);
        ReflectionTestUtils.setField(outboxPublisher, "staleTimeoutSeconds", 30L);
    }

    @Test
    void READY_event를_선점하고_발행한_뒤_PUBLISHED로_변경한다() {
        OutboxEventSnapshot event = event();
        when(outboxEventStateService.findReadyEvents(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(event));
        when(outboxEventStateService.claim(eq(event.eventId()), any(String.class), any(LocalDateTime.class)))
                .thenReturn(true);

        outboxPublisher.publishReadyEvents();

        verify(reservationHoldRequestPublisher).publish(event.eventId(), event.payload());
        verify(outboxEventStateService).markPublished(
                eq(event.eventId()),
                any(String.class),
                any(LocalDateTime.class)
        );
    }

    @Test
    void 다른_Publisher가_먼저_선점한_event는_발행하지_않는다() {
        OutboxEventSnapshot event = event();
        when(outboxEventStateService.findReadyEvents(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(event));
        when(outboxEventStateService.claim(eq(event.eventId()), any(String.class), any(LocalDateTime.class)))
                .thenReturn(false);

        outboxPublisher.publishReadyEvents();

        verify(reservationHoldRequestPublisher, never()).publish(any(String.class), any(String.class));
    }

    @Test
    void RabbitMQ_발행에_실패하면_Outbox_재시도_상태를_기록한다() {
        OutboxEventSnapshot event = event();
        when(outboxEventStateService.findReadyEvents(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(event));
        when(outboxEventStateService.claim(eq(event.eventId()), any(String.class), any(LocalDateTime.class)))
                .thenReturn(true);
        doThrow(new IllegalStateException("publisher nack"))
                .when(reservationHoldRequestPublisher)
                .publish(event.eventId(), event.payload());

        outboxPublisher.publishReadyEvents();

        verify(outboxEventStateService).markPublishFailed(
                eq(event),
                any(String.class),
                any(LocalDateTime.class),
                eq("publisher nack")
        );
        verify(outboxEventStateService, never()).markPublished(
                any(String.class),
                any(String.class),
                any(LocalDateTime.class)
        );
    }

    private OutboxEventSnapshot event() {
        return new OutboxEventSnapshot(
                "event-1",
                "RESERVATION_HOLD_REQUESTED",
                "{\"messageId\":\"event-1\"}",
                0
        );
    }
}
