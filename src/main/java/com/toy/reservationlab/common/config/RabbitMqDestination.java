package com.toy.reservationlab.common.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RabbitMqDestination {

    /*
     * RabbitMQ 목적지는 exchange, queue, routing key를 한 세트로 다룬다.
     * publisher와 broker 설정이 같은 값을 공유해야 하므로 enum으로 묶어 관리한다.
     */
    RESERVATION_HOLD_REQUEST(
            "reservation.hold.exchange",
            "reservation.hold.request.queue",
            "reservation.hold.request"
    );

    private final String exchangeName;
    private final String queueName;
    private final String routingKey;
}
