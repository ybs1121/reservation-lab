package com.toy.reservationlab.common.inbox;

public enum InboxMessageStatus {
    // 현재 DB 트랜잭션이 messageId의 처리 권한을 선점한 상태다.
    RECEIVED,
    // 비즈니스 성공 또는 예측 가능한 비즈니스 실패까지 처리가 끝난 상태다.
    PROCESSED
}
