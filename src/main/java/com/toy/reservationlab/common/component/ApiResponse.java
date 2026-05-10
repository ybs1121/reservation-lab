package com.toy.reservationlab.common.component;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String code
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> empty() {
        return success(null);
    }

    public static ApiResponse<Void> fail(String message, String code) {
        return new ApiResponse<>(false, null, message, code);
    }
}
