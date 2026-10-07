package com.toy.reservationlab.common.outbox;

public enum OutboxEventStatus {
    // 아직 Publisher가 발행 권한을 얻지 않은 상태다.
    READY,
    // 한 Publisher가 조건부 선점한 상태다.
    SENDING,
    // RabbitMQ publisher confirm ACK를 받은 상태다.
    PUBLISHED,
    // 발행 재시도를 모두 소진한 상태다.
    DEAD
}
