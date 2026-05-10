package com.toy.reservationlab.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "`user`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private static final String NOT_DELETED = "N";
    private static final String DELETED = "Y";

    @Id
    @Column(name = "user_id", length = 39, nullable = false)
    private String userId;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "phone", length = 20, nullable = false, unique = true)
    private String phone;

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

    private User(
            String userId,
            String name,
            String phone,
            String createdBy
    ) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.delYn = NOT_DELETED;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    public static User create(
            String userId,
            String name,
            String phone,
            String createdBy
    ) {
        return new User(userId, name, phone, createdBy);
    }

    public void update(String name, String phone, String updatedBy) {
        this.name = name;
        this.phone = phone;
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
