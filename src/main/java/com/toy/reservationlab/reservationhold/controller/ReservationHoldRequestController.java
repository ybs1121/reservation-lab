package com.toy.reservationlab.reservationhold.controller;

import com.toy.reservationlab.common.component.ApiResponse;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldRequestCreateRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldRequestResponse;
import com.toy.reservationlab.reservationhold.service.ReservationHoldRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservation-hold-requests")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservation-lab.reservation-hold-request.enabled", havingValue = "true")
public class ReservationHoldRequestController {

    private final ReservationHoldRequestService reservationHoldRequestService;

    @PostMapping
    public ApiResponse<ReservationHoldRequestResponse> createRequest(
            @RequestBody ReservationHoldRequestCreateRequest request
    ) {
        return ApiResponse.success(reservationHoldRequestService.createRequest(request));
    }

    @GetMapping("/{requestId}")
    public ApiResponse<ReservationHoldRequestResponse> getRequest(@PathVariable String requestId) {
        return ApiResponse.success(reservationHoldRequestService.getRequest(requestId));
    }
}
