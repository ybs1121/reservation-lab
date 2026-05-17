-- ===========================================
-- 테이블 컬럼 순서를 엔티티 필드 순서에 맞춤
-- ===========================================

-- user 테이블: user_id, name, phone, del_yn, created_at, created_by, updated_at, updated_by
ALTER TABLE `user`
    MODIFY COLUMN `name` varchar(50) NOT NULL AFTER `user_id`,
    MODIFY COLUMN `phone` varchar(20) NOT NULL AFTER `name`,
    MODIFY COLUMN `del_yn` varchar(1) NOT NULL AFTER `phone`,
    MODIFY COLUMN `created_at` datetime(6) NOT NULL AFTER `del_yn`,
    MODIFY COLUMN `created_by` varchar(255) NOT NULL AFTER `created_at`,
    MODIFY COLUMN `updated_at` datetime(6) NOT NULL AFTER `created_by`,
    MODIFY COLUMN `updated_by` varchar(255) NOT NULL AFTER `updated_at`;

-- restaurant 테이블: restaurant_id, name, address, status, del_yn, created_at, created_by, updated_at, updated_by
ALTER TABLE `restaurant`
    MODIFY COLUMN `name` varchar(100) NOT NULL AFTER `restaurant_id`,
    MODIFY COLUMN `address` varchar(255) NOT NULL AFTER `name`,
    MODIFY COLUMN `status` enum('CLOSED','OPEN','SUSPENDED') NOT NULL AFTER `address`,
    MODIFY COLUMN `del_yn` varchar(1) NOT NULL AFTER `status`,
    MODIFY COLUMN `created_at` datetime(6) NOT NULL AFTER `del_yn`,
    MODIFY COLUMN `created_by` varchar(255) NOT NULL AFTER `created_at`,
    MODIFY COLUMN `updated_at` datetime(6) NOT NULL AFTER `created_by`,
    MODIFY COLUMN `updated_by` varchar(255) NOT NULL AFTER `updated_at`;

-- reservation_slot 테이블: slot_id, restaurant_id, slot_date, slot_time, capacity, status, del_yn, created_at, created_by, updated_at, updated_by
ALTER TABLE `reservation_slot`
    MODIFY COLUMN `restaurant_id` varchar(39) NOT NULL AFTER `slot_id`,
    MODIFY COLUMN `slot_date` date NOT NULL AFTER `restaurant_id`,
    MODIFY COLUMN `slot_time` varchar(5) NOT NULL AFTER `slot_date`,
    MODIFY COLUMN `capacity` int NOT NULL AFTER `slot_time`,
    MODIFY COLUMN `status` enum('AVAILABLE','CLOSED','FULL') NOT NULL AFTER `capacity`,
    MODIFY COLUMN `del_yn` varchar(1) NOT NULL AFTER `status`,
    MODIFY COLUMN `created_at` datetime(6) NOT NULL AFTER `del_yn`,
    MODIFY COLUMN `created_by` varchar(255) NOT NULL AFTER `created_at`,
    MODIFY COLUMN `updated_at` datetime(6) NOT NULL AFTER `created_by`,
    MODIFY COLUMN `updated_by` varchar(255) NOT NULL AFTER `updated_at`;

-- reservation 테이블: reservation_id, slot_id, user_id, party_size, status, del_yn, created_at, created_by, updated_at, updated_by
ALTER TABLE `reservation`
    MODIFY COLUMN `slot_id` varchar(39) NOT NULL AFTER `reservation_id`,
    MODIFY COLUMN `user_id` varchar(39) NOT NULL AFTER `slot_id`,
    MODIFY COLUMN `party_size` int NOT NULL AFTER `user_id`,
    MODIFY COLUMN `status` enum('CANCELLED','CONFIRMED','NO_SHOW') NOT NULL AFTER `party_size`,
    MODIFY COLUMN `del_yn` varchar(1) NOT NULL AFTER `status`,
    MODIFY COLUMN `created_at` datetime(6) NOT NULL AFTER `del_yn`,
    MODIFY COLUMN `created_by` varchar(255) NOT NULL AFTER `created_at`,
    MODIFY COLUMN `updated_at` datetime(6) NOT NULL AFTER `created_by`,
    MODIFY COLUMN `updated_by` varchar(255) NOT NULL AFTER `updated_at`;
