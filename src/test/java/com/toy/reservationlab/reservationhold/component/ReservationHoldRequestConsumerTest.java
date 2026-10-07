package com.toy.reservationlab.reservationhold.component;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationHoldRequestConsumerTest {

    @Mock
    private ReservationHoldMessageSerializer messageSerializer;

    @Mock
    private ReservationHoldInboxHandler reservationHoldInboxHandler;

    @InjectMocks
    private ReservationHoldRequestConsumer reservationHoldRequestConsumer;

    @Test
    void queue에서_받은_JSON_메시지를_역직렬화해_Inbox_handler에_위임한다() {
        ReservationHoldRequestedMessage message = new ReservationHoldRequestedMessage(
                "message-1",
                "RESERVATION_HOLD_REQUESTED",
                1,
                "2026-07-19T14:00:00",
                new ReservationHoldRequestedPayload("request-1", "slot-1", "user-1", 1)
        );
        when(messageSerializer.deserialize("message-json")).thenReturn(message);

        reservationHoldRequestConsumer.consume("message-json");

        verify(reservationHoldInboxHandler).handle(message);
    }
}
