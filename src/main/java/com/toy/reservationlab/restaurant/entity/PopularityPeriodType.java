package com.toy.reservationlab.restaurant.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PopularityPeriodType {

    // 전체 기간 집계는 시작일 없이 모든 예약 이력을 대상으로 한다.
    ALL_TIME(null),

    // 인기 음식점 배치는 고정된 기간 정책만 사전 집계한다.
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
    LAST_90_DAYS(90);

    private final Integer periodDays;

    /**
     * Batch Step이 기간별 Reader를 만들 때 사용하는 조회 시작 시각이다.
     * 전체 기간은 시작 시각이 필요 없으므로 null을 반환한다.
     */
    public LocalDateTime resolveStartedAt(LocalDateTime runAt) {
        if (periodDays == null) {
            return null;
        }
        return runAt.minusDays(periodDays);
    }
}
