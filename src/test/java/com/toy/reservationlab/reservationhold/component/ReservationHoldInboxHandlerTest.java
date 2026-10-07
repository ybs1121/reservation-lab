package com.toy.reservationlab.reservationhold.component;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toy.reservationlab.common.inbox.InboxMessageRepository;
import com.toy.reservationlab.common.inbox.InboxMessageStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationHoldInboxHandlerTest {

    @Mock
    private InboxMessageRepository inboxMessageRepository;

    @Mock
    private ReservationHoldRequestProcessor reservationHoldRequestProcessor;

    @InjectMocks
    private ReservationHoldInboxHandler reservationHoldInboxHandler;

    @Test
    void 처음_수신한_messageId만_비즈니스_처리한다() {
        ReservationHoldRequestedMessage message = message();
        when(inboxMessageRepository.insertIfAbsent(
                eq(message.messageId()),
                eq(message.payload().requestId()),
                eq(message.eventType()),
                any(LocalDateTime.class)
        )).thenReturn(1);

        reservationHoldInboxHandler.handle(message);

        verify(reservationHoldRequestProcessor).process(message.payload().requestId());
        verify(inboxMessageRepository).markProcessed(
                eq(message.messageId()),
                any(LocalDateTime.class),
                eq(InboxMessageStatus.RECEIVED),
                eq(InboxMessageStatus.PROCESSED)
        );
    }

    @Test
    void 이미_처리한_messageId는_비즈니스_처리를_반복하지_않는다() {
        ReservationHoldRequestedMessage message = message();
        when(inboxMessageRepository.insertIfAbsent(
                eq(message.messageId()),
                eq(message.payload().requestId()),
                eq(message.eventType()),
                any(LocalDateTime.class)
        )).thenReturn(0);

        reservationHoldInboxHandler.handle(message);

        verify(reservationHoldRequestProcessor, never()).process(any(String.class));
        verify(inboxMessageRepository, never()).markProcessed(
                any(String.class),
                any(LocalDateTime.class),
                any(InboxMessageStatus.class),
                any(InboxMessageStatus.class)
        );
    }

    private ReservationHoldRequestedMessage message() {
        return new ReservationHoldRequestedMessage(
                "message-1",
                "RESERVATION_HOLD_REQUESTED",
                1,
                "2026-07-19T14:00:00",
                new ReservationHoldRequestedPayload("request-1", "slot-1", "user-1", 1)
        );
    }
}
