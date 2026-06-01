package com.toy.reservationlab.reservationhold.component;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationHoldRequestConsumerTest {

    @Mock
    private ReservationHoldRequestProcessor reservationHoldRequestProcessor;

    @InjectMocks
    private ReservationHoldRequestConsumer reservationHoldRequestConsumer;

    @Test
    void queue에서_받은_requestId를_processor에_위임한다() {
        reservationHoldRequestConsumer.consume("hold-request-1");

        verify(reservationHoldRequestProcessor).process("hold-request-1");
    }
}
