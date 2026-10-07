package com.toy.reservationlab.reservationhold.component;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.toy.reservationlab.common.config.RabbitMqDestination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class ReservationHoldRequestPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ReservationHoldRequestPublisher reservationHoldRequestPublisher;

    @Test
    void Outbox_payload를_정해진_exchange와_routing_key로_발행하고_ACK를_확인한다() {
        RabbitMqDestination destination = RabbitMqDestination.RESERVATION_HOLD_REQUEST;
        String payload = "{\"requestId\":\"hold-request-1\"}";
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(destination.getExchangeName()),
                eq(destination.getRoutingKey()),
                eq(payload),
                any(CorrelationData.class)
        );

        reservationHoldRequestPublisher.publish("message-1", payload);

        verify(rabbitTemplate).convertAndSend(
                eq(destination.getExchangeName()),
                eq(destination.getRoutingKey()),
                eq(payload),
                any(CorrelationData.class)
        );
    }
}
