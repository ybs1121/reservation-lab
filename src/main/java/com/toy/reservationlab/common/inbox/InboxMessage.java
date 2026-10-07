package com.toy.reservationlab.common.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inbox_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxMessage {

    @Id
    @Column(name = "message_id", length = 39, nullable = false)
    private String messageId;

    @Column(name = "request_id", length = 39, nullable = false)
    private String requestId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InboxMessageStatus status;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
