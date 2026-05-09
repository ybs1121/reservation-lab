package com.toy.reservationlab.reservationslot.entity;

public enum ReservationSlotStatus {
    // 예약 생성이 가능한 슬롯
    AVAILABLE,

    // 수용 인원이 모두 소진된 슬롯
    FULL,

    // 수동으로 닫힌 슬롯
    CLOSED
}
