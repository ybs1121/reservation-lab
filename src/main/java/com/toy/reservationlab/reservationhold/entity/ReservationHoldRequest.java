package com.toy.reservationlab.reservationhold.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservation_hold_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationHoldRequest {

    public static final String DEFAULT_FAILURE_MESSAGE = "요청 처리에 실패했습니다. 다시 시도해 주세요.";

    /*
     * RabbitMQ 메시지에는 requestId만 담고, 실제 요청 내용과 처리 상태는 DB row에 남긴다.
     * 이렇게 두면 메시지 payload를 작게 유지하면서도 사용자가 나중에 처리 결과를 조회할 수 있다.
     */

    @Id
    @Column(name = "request_id", length = 39, nullable = false)
    private String requestId;

    @Column(name = "slot_id", length = 39, nullable = false)
    private String slotId;

    @Column(name = "user_id", length = 39, nullable = false)
    private String userId;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReservationHoldRequestStatus status;

    @Column(name = "hold_id", length = 39)
    private String holdId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    private ReservationHoldRequest(
            String requestId,
            String slotId,
            String userId,
            int partySize,
            String createdBy
    ) {
        this.requestId = requestId;
        this.slotId = slotId;
        this.userId = userId;
        this.partySize = partySize;
        this.status = ReservationHoldRequestStatus.PENDING;
        this.retryCount = 0;
        this.requestedAt = LocalDateTime.now();
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    public static ReservationHoldRequest create(
            String requestId,
            String slotId,
            String userId,
            int partySize,
            String createdBy
    ) {
        return new ReservationHoldRequest(requestId, slotId, userId, partySize, createdBy);
    }

    /**
     * consumer가 메시지를 가져가 실제 hold 생성 로직을 시작했음을 기록한다.
     * 장애 분석 시 PENDING과 PROCESSING을 구분할 수 있게 하기 위한 상태 전환이다.
     */
    public void startProcessing(String updatedBy) {
        this.status = ReservationHoldRequestStatus.PROCESSING;
        this.updatedBy = updatedBy;
    }

    /**
     * hold 생성 또는 기존 hold 재사용에 성공했을 때 최종 결과를 저장한다.
     * 이후 조회 API는 이 holdId를 보고 사용자가 다음 확정 단계로 갈 수 있게 한다.
     */
    public void succeed(String holdId, String updatedBy) {
        this.status = ReservationHoldRequestStatus.SUCCEEDED;
        this.holdId = holdId;
        this.failureCode = null;
        this.failureMessage = null;
        this.processedAt = LocalDateTime.now();
        this.updatedBy = updatedBy;
    }

    /**
     * 비즈니스 실패나 재시도 후 시스템 실패를 사용자 조회 가능한 최종 실패 상태로 남긴다.
     * 사용자 메시지는 단순하게 유지하고, 개발자용 구분은 failureCode로 남긴다.
     */
    public void fail(String failureCode, String updatedBy) {
        this.status = ReservationHoldRequestStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = DEFAULT_FAILURE_MESSAGE;
        this.processedAt = LocalDateTime.now();
        this.updatedBy = updatedBy;
    }

    public void increaseRetryCount(String updatedBy) {
        this.retryCount++;
        this.updatedBy = updatedBy;
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
