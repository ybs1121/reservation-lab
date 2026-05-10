package com.toy.reservationlab.restaurant.entity;

public enum RestaurantStatus {
    // 운영 중인 식당
    OPEN,

    // 운영 종료 또는 닫힌 식당
    CLOSED,

    // 예약 슬롯 생성을 일시 중지한 식당
    SUSPENDED
}
