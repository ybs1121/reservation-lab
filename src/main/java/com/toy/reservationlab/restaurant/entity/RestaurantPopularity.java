package com.toy.reservationlab.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "restaurant_popularity",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_restaurant_popularity_restaurant_period",
                        columnNames = {"restaurant_id", "period_type"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantPopularity {

    /*
     * 예약 원본 데이터를 매번 집계하지 않기 위한 읽기 모델이다.
     * Batch Job이 이 테이블을 갱신하고, 인기 음식점 API는 이 결과를 조회한다.
     */

    @Id
    @Column(name = "popularity_id", length = 39, nullable = false)
    private String popularityId;

    @Column(name = "restaurant_id", length = 39, nullable = false)
    private String restaurantId;

    /*
     * 같은 Restaurant이라도 전체 기간, 7일, 30일, 90일 집계가 각각 필요하므로
     * 기간 정책을 enum으로 고정해 저장한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", length = 20, nullable = false)
    private PopularityPeriodType periodType;

    /*
     * 조회 편의와 운영 디버깅을 위해 enum의 일수 값을 함께 저장한다.
     * ALL_TIME은 기간 제한이 없으므로 null이다.
     */
    @Column(name = "period_days")
    private Integer periodDays;

    @Column(name = "reservation_count", nullable = false)
    private long reservationCount;

    @Column(name = "aggregated_at", nullable = false)
    private LocalDateTime aggregatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    private RestaurantPopularity(
            String popularityId,
            String restaurantId,
            PopularityPeriodType periodType,
            long reservationCount,
            LocalDateTime aggregatedAt,
            String createdBy
    ) {
        this.popularityId = popularityId;
        this.restaurantId = restaurantId;
        this.periodType = periodType;
        this.periodDays = periodType.getPeriodDays();
        this.reservationCount = reservationCount;
        this.aggregatedAt = aggregatedAt;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    public static RestaurantPopularity create(
            String popularityId,
            String restaurantId,
            PopularityPeriodType periodType,
            long reservationCount,
            LocalDateTime aggregatedAt,
            String createdBy
    ) {
        return new RestaurantPopularity(
                popularityId,
                restaurantId,
                periodType,
                reservationCount,
                aggregatedAt,
                createdBy
        );
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
