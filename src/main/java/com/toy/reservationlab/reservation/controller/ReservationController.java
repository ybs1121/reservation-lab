package com.toy.reservationlab.reservation.controller;

import com.toy.reservationlab.common.component.ApiResponse;
import com.toy.reservationlab.reservation.dto.ReservationCreateRequest;
import com.toy.reservationlab.reservation.dto.ReservationDeleteRequest;
import com.toy.reservationlab.reservation.dto.ReservationResponse;
import com.toy.reservationlab.reservation.dto.ReservationUpdateRequest;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.service.ReservationService;
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
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ApiResponse<ReservationResponse> createReservation(@RequestBody ReservationCreateRequest request) {
        Reservation reservation = reservationService.createReservation(
                request.reservationId(),
                request.slotId(),
                request.userId(),
                request.partySize(),
                request.createdBy()
        );
        return ApiResponse.success(ReservationResponse.from(reservation));
    }

    @GetMapping("/{reservationId}")
    public ApiResponse<ReservationResponse> getReservation(@PathVariable String reservationId) {
        Reservation reservation = reservationService.getReservation(reservationId);
        return ApiResponse.success(ReservationResponse.from(reservation));
    }

    @PutMapping("/{reservationId}")
    public ApiResponse<ReservationResponse> updateReservation(
            @PathVariable String reservationId,
            @RequestBody ReservationUpdateRequest request
    ) {
        Reservation reservation = reservationService.updateReservationStatus(
                reservationId,
                request.status(),
                request.updatedBy()
        );
        return ApiResponse.success(ReservationResponse.from(reservation));
    }

    @DeleteMapping("/{reservationId}")
    public ApiResponse<ReservationResponse> deleteReservation(
            @PathVariable String reservationId,
            @RequestBody ReservationDeleteRequest request
    ) {
        Reservation reservation = reservationService.deleteReservation(reservationId, request.updatedBy());
        return ApiResponse.success(ReservationResponse.from(reservation));
    }
}
