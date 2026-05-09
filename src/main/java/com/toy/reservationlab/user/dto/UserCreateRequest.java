package com.toy.reservationlab.user.dto;

public record UserCreateRequest(
        String userId,
        String name,
        String phone,
        String createdBy
) {
}
