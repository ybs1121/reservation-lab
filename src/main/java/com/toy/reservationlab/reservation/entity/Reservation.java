package com.toy.reservationlab.reservation.entity;

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
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    private static final String NOT_DELETED = "N";
    private static final String DELETED = "Y";

    @Id
    @Column(name = "reservation_id", length = 39, nullable = false)
    private String reservationId;

    @Column(name = "slot_id", length = 39, nullable = false)
    private String slotId;

    @Column(name = "user_id", length = 39, nullable = false)
    private String userId;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReservationStatus status;

    @Column(name = "del_yn", length = 1, nullable = false)
    private String delYn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    private Reservation(
            String reservationId,
            String slotId,
            String userId,
            int partySize,
            ReservationStatus status,
            String createdBy
    ) {
        this.reservationId = reservationId;
        this.slotId = slotId;
        this.userId = userId;
        this.partySize = partySize;
        this.status = status;
        this.delYn = NOT_DELETED;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    public static Reservation create(
            String reservationId,
            String slotId,
            String userId,
            int partySize,
            ReservationStatus status,
            String createdBy
    ) {
        return new Reservation(reservationId, slotId, userId, partySize, status, createdBy);
    }

    public boolean isDeleted() {
        return DELETED.equals(delYn);
    }

    public void cancel(String updatedBy) {
        this.status = ReservationStatus.CANCELLED;
        this.updatedBy = updatedBy;
    }

    public void markDeleted(String updatedBy) {
        this.delYn = DELETED;
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
