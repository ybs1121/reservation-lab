package com.toy.reservationlab.reservationhold.component;

import com.toy.reservationlab.common.config.RabbitMqDestination;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservation-lab.reservation-hold-request.enabled", havingValue = "true")
public class ReservationHoldRequestPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${reservation-lab.outbox-publisher.confirm-timeout-seconds:5}")
    private long confirmTimeoutSeconds;

    /**
     * 메시지에는 requestId만 담는다.
     * 실제 요청 데이터와 처리 상태는 DB row에 있으므로 consumer는 requestId로 DB를 다시 조회한다.
     */
    public void publish(String messageId, String payload) {
        RabbitMqDestination destination = RabbitMqDestination.RESERVATION_HOLD_REQUEST;
        CorrelationData correlationData = new CorrelationData(messageId);
        rabbitTemplate.convertAndSend(
                destination.getExchangeName(),
                destination.getRoutingKey(),
                payload,
                correlationData
        );
        waitForConfirm(correlationData);
    }

    private void waitForConfirm(CorrelationData correlationData) {
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeoutSeconds, TimeUnit.SECONDS);
            ReturnedMessage returnedMessage = correlationData.getReturned();
            if (returnedMessage != null) {
                throw new IllegalStateException("RabbitMQ returned message: " + returnedMessage.getReplyText());
            }
            if (!confirm.ack()) {
                throw new IllegalStateException("RabbitMQ publisher nack: " + confirm.reason());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RabbitMQ publisher confirm interrupted.", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("RabbitMQ publisher confirm failed.", exception);
        }
    }
}
