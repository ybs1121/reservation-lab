package com.toy.reservationlab.reservationhold.component;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservation-lab.reservation-hold-request.enabled", havingValue = "true")
public class ReservationHoldRequestEventListener {

    private final ReservationHoldRequestPublisher reservationHoldRequestPublisher;

    /**
     * 요청 row가 commit된 뒤에만 RabbitMQ로 발행한다.
     * fallbackExecution=true는 트랜잭션이 없는 테스트/호출 경로에서는 즉시 실행하라는 의미다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publishAfterCommit(ReservationHoldRequestCreatedEvent event) {
        reservationHoldRequestPublisher.publish(event.requestId());
    }
}
