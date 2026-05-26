package com.toy.reservationlab.reservationhold.controller;

import com.toy.reservationlab.common.component.ApiResponse;
import com.toy.reservationlab.reservation.dto.ReservationResponse;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldConfirmRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldCreateRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldReleaseRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldResponse;
import com.toy.reservationlab.reservationhold.service.ReservationHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservation-holds")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservation-lab.reservation-hold.enabled", havingValue = "true")
public class ReservationHoldController {

    private final ReservationHoldService reservationHoldService;

    @PostMapping
    public ApiResponse<ReservationHoldResponse> createHold(@RequestBody ReservationHoldCreateRequest request) {
        return ApiResponse.success(reservationHoldService.createHold(request));
    }

    @GetMapping("/{holdId}")
    public ApiResponse<ReservationHoldResponse> getHold(@PathVariable String holdId) {
        return ApiResponse.success(reservationHoldService.getHold(holdId));
    }

    @PostMapping("/{holdId}/confirm")
    public ApiResponse<ReservationResponse> confirmHold(
            @PathVariable String holdId,
            @RequestBody ReservationHoldConfirmRequest request
    ) {
        Reservation reservation = reservationHoldService.confirmHold(holdId, request);
        return ApiResponse.success(ReservationResponse.from(reservation));
    }

    @DeleteMapping("/{holdId}")
    public ApiResponse<Void> releaseHold(
            @PathVariable String holdId,
            @RequestBody ReservationHoldReleaseRequest request
    ) {
        reservationHoldService.releaseHold(holdId, request.userId());
        return ApiResponse.empty();
    }
}
