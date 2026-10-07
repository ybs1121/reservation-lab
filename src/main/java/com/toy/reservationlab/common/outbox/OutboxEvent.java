package com.toy.reservationlab.common.outbox;

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
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @Column(name = "event_id", length = 39, nullable = false)
    private String eventId;

    @Column(name = "aggregate_type", length = 100, nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 39, nullable = false)
    private String aggregateId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "payload_version", nullable = false)
    private int payloadVersion;

    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claim_id", length = 39)
    private String claimId;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private OutboxEvent(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            int payloadVersion,
            String payload,
            LocalDateTime occurredAt
    ) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payloadVersion = payloadVersion;
        this.payload = payload;
        this.status = OutboxEventStatus.READY;
        this.retryCount = 0;
        this.nextAttemptAt = occurredAt;
        this.occurredAt = occurredAt;
    }

    public static OutboxEvent create(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            int payloadVersion,
            String payload,
            LocalDateTime occurredAt
    ) {
        return new OutboxEvent(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                payloadVersion,
                payload,
                occurredAt
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
