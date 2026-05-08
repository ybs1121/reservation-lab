package com.toy.reservationlab.restaurant.entity;

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
@Table(name = "restaurant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Restaurant {

    private static final String NOT_DELETED = "N";
    private static final String DELETED = "Y";

    @Id
    @Column(name = "restaurant_id", length = 39, nullable = false)
    private String restaurantId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "address", length = 255, nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RestaurantStatus status;

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

    private Restaurant(
            String restaurantId,
            String name,
            String address,
            RestaurantStatus status,
            String createdBy
    ) {
        this.restaurantId = restaurantId;
        this.name = name;
        this.address = address;
        this.status = status;
        this.delYn = NOT_DELETED;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    public static Restaurant create(
            String restaurantId,
            String name,
            String address,
            RestaurantStatus status,
            String createdBy
    ) {
        return new Restaurant(restaurantId, name, address, status, createdBy);
    }

    public boolean canCreateReservationSlot() {
        return status == RestaurantStatus.OPEN && !isDeleted();
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

