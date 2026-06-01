package com.toy.reservationlab.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "reservation-lab.reservation-hold-request.enabled", havingValue = "true")
public class RabbitMqConfig {

    /**
     * DirectExchange는 routing key가 정확히 일치하는 queue로 메시지를 보낸다.
     * 이번 단계는 hold 생성 요청 한 종류만 다루므로 가장 단순한 direct 방식을 사용한다.
     */
    @Bean
    public DirectExchange reservationHoldExchange() {
        return new DirectExchange(RabbitMqDestination.RESERVATION_HOLD_REQUEST.getExchangeName());
    }

    /**
     * Queue는 consumer가 처리하기 전까지 hold 생성 요청 메시지가 대기하는 공간이다.
     * durable=true로 두어 RabbitMQ가 재시작되어도 queue 정의 자체는 유지되게 한다.
     */
    @Bean
    public Queue reservationHoldRequestQueue() {
        return new Queue(RabbitMqDestination.RESERVATION_HOLD_REQUEST.getQueueName(), true);
    }

    /**
     * Binding은 exchange와 queue를 routing key로 연결한다.
     * publisher가 같은 routing key로 메시지를 보내면 이 queue에 메시지가 쌓인다.
     */
    @Bean
    public Binding reservationHoldRequestBinding(
            DirectExchange reservationHoldExchange,
            Queue reservationHoldRequestQueue
    ) {
        return BindingBuilder.bind(reservationHoldRequestQueue)
                .to(reservationHoldExchange)
                .with(RabbitMqDestination.RESERVATION_HOLD_REQUEST.getRoutingKey());
    }
}
