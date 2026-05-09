package com.toy.reservationlab.user.controller;

import com.toy.reservationlab.common.component.ApiResponse;
import com.toy.reservationlab.user.dto.UserCreateRequest;
import com.toy.reservationlab.user.dto.UserDeleteRequest;
import com.toy.reservationlab.user.dto.UserResponse;
import com.toy.reservationlab.user.dto.UserUpdateRequest;
import com.toy.reservationlab.user.entity.User;
import com.toy.reservationlab.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ApiResponse<UserResponse> createUser(@RequestBody UserCreateRequest request) {
        User user = userService.createUser(
                request.userId(),
                request.name(),
                request.phone(),
                request.createdBy()
        );
        return ApiResponse.success(UserResponse.from(user));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getUser(@PathVariable String userId) {
        User user = userService.getUser(userId);
        return ApiResponse.success(UserResponse.from(user));
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable String userId,
            @RequestBody UserUpdateRequest request
    ) {
        User user = userService.updateUser(
                userId,
                request.name(),
                request.phone(),
                request.updatedBy()
        );
        return ApiResponse.success(UserResponse.from(user));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<UserResponse> deleteUser(
            @PathVariable String userId,
            @RequestBody UserDeleteRequest request
    ) {
        User user = userService.deleteUser(userId, request.updatedBy());
        return ApiResponse.success(UserResponse.from(user));
    }
}
