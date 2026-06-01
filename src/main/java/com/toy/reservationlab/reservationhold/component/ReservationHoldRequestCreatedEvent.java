package com.toy.reservationlab.reservationhold.component;

/**
 * hold 요청 row가 생성되었음을 애플리케이션 내부에 알리는 이벤트다.
 * RabbitMQ 메시지와 마찬가지로 requestId만 전달하고, 실제 요청 내용은 DB에서 다시 조회한다.
 */
public record ReservationHoldRequestCreatedEvent(
        String requestId
) {
}
