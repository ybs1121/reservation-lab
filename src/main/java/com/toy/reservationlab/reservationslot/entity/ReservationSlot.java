package com.toy.reservationlab.reservationslot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservation_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSlot {

    private static final String NOT_DELETED = "N";
    private static final String DELETED = "Y";

    @Id
    @Column(name = "slot_id", length = 39, nullable = false)
    private String slotId;

    @Column(name = "restaurant_id", length = 39, nullable = false)
    private String restaurantId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", length = 5, nullable = false)
    private String slotTime;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReservationSlotStatus status;

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

    private ReservationSlot(
            String slotId,
            String restaurantId,
            LocalDate slotDate,
            String slotTime,
            int capacity,
            ReservationSlotStatus status,
            String createdBy
    ) {
        this.slotId = slotId;
        this.restaurantId = restaurantId;
        this.slotDate = slotDate;
        this.slotTime = slotTime;
        this.capacity = capacity;
        this.status = status;
        this.delYn = NOT_DELETED;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    public static ReservationSlot create(
            String slotId,
            String restaurantId,
            LocalDate slotDate,
            String slotTime,
            int capacity,
            ReservationSlotStatus status,
            String createdBy
    ) {
        return new ReservationSlot(slotId, restaurantId, slotDate, slotTime, capacity, status, createdBy);
    }

    public boolean canCreateReservation() {
        return status == ReservationSlotStatus.AVAILABLE && !isDeleted();
    }

    public boolean hasCapacityFor(int activePartySize, int partySize) {
        return activePartySize + partySize <= capacity;
    }

    public boolean isFull() {
        return status == ReservationSlotStatus.FULL;
    }

    public boolean isReducingCapacity(int capacity) {
        return capacity < this.capacity;
    }

    public void markFullIfCapacityReached(int activePartySize, String updatedBy) {
        if (activePartySize >= capacity) {
            this.status = ReservationSlotStatus.FULL;
            this.updatedBy = updatedBy;
        }
    }

    public void restoreAvailableIfNotFull(int activePartySize, String updatedBy) {
        if (isFull() && activePartySize < capacity) {
            this.status = ReservationSlotStatus.AVAILABLE;
            this.updatedBy = updatedBy;
        }
    }

    public void update(
            String restaurantId,
            LocalDate slotDate,
            String slotTime,
            int capacity,
            ReservationSlotStatus status,
            String updatedBy
    ) {
        this.restaurantId = restaurantId;
        this.slotDate = slotDate;
        this.slotTime = slotTime;
        this.capacity = capacity;
        this.status = status;
        this.updatedBy = updatedBy;
    }

    public boolean isDeleted() {
        return DELETED.equals(delYn);
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
