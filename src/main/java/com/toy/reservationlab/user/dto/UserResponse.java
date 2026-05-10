package com.toy.reservationlab.user.dto;

import com.toy.reservationlab.user.entity.User;

public record UserResponse(
        String userId,
        String name,
        String phone,
        String delYn
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getName(),
                user.getPhone(),
                user.getDelYn()
        );
    }
}
