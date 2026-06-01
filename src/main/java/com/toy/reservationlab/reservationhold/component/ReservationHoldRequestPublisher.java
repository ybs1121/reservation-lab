package com.toy.reservationlab.reservationhold.component;

import com.toy.reservationlab.common.config.RabbitMqDestination;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservation-lab.reservation-hold-request.enabled", havingValue = "true")
public class ReservationHoldRequestPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 메시지에는 requestId만 담는다.
     * 실제 요청 데이터와 처리 상태는 DB row에 있으므로 consumer는 requestId로 DB를 다시 조회한다.
     */
    public void publish(String requestId) {
        RabbitMqDestination destination = RabbitMqDestination.RESERVATION_HOLD_REQUEST;
        rabbitTemplate.convertAndSend(
                destination.getExchangeName(),
                destination.getRoutingKey(),
                requestId
        );
    }
}
