package com.toy.reservationlab.user.dto;

public record UserUpdateRequest(
        String name,
        String phone,
        String updatedBy
) {
}
