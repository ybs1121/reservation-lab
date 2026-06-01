package com.toy.reservationlab.reservationhold.component;

import static org.mockito.Mockito.verify;

import com.toy.reservationlab.common.config.RabbitMqDestination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class ReservationHoldRequestPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ReservationHoldRequestPublisher reservationHoldRequestPublisher;

    @Test
    void hold_요청_requestId를_정해진_exchange와_routing_key로_발행한다() {
        reservationHoldRequestPublisher.publish("hold-request-1");

        RabbitMqDestination destination = RabbitMqDestination.RESERVATION_HOLD_REQUEST;
        verify(rabbitTemplate).convertAndSend(
                destination.getExchangeName(),
                destination.getRoutingKey(),
                "hold-request-1"
        );
    }
}
