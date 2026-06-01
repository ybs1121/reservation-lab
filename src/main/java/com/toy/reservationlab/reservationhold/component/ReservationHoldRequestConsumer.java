package com.toy.reservationlab.reservationhold.component;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("""
        '${reservation-lab.reservation-hold-request.enabled:false}' == 'true'
        && '${reservation-lab.reservation-hold.enabled:false}' == 'true'
        """)
public class ReservationHoldRequestConsumer {

    private final ReservationHoldRequestProcessor reservationHoldRequestProcessor;

    /**
     * Queue에서 꺼낸 메시지는 requestId 하나뿐이다.
     * 실제 검증과 hold 생성은 processor로 위임해 listener는 메시지 입구 역할만 맡는다.
     */
    @RabbitListener(queues = "#{T(com.toy.reservationlab.common.config.RabbitMqDestination).RESERVATION_HOLD_REQUEST.getQueueName()}")
    public void consume(String requestId) {
        reservationHoldRequestProcessor.process(requestId);
    }
}
