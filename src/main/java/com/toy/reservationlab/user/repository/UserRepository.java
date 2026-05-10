package com.toy.reservationlab.user.repository;

import com.toy.reservationlab.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByPhone(String phone);

    long countByPhoneAndUserIdNot(String phone, String userId);
}
