package com.toy.reservationlab.reservationhold.entity;

public enum ReservationHoldRequestStatus {

    // API가 요청을 DB에 저장했고, 아직 consumer가 처리하지 않은 상태다.
    PENDING,

    // consumer가 메시지를 받아 기존 hold 생성 로직을 실행 중인 상태다.
    PROCESSING,

    // hold 생성 또는 기존 hold 재사용에 성공해 holdId가 저장된 상태다.
    SUCCEEDED,

    // 비즈니스 실패 또는 재시도 후 시스템 실패로 더 처리하지 않는 상태다.
    FAILED
}
