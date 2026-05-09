package com.toy.reservationlab.reservationslot.controller;

import com.toy.reservationlab.common.component.ApiResponse;
import com.toy.reservationlab.reservationslot.dto.ReservationSlotCreateRequest;
import com.toy.reservationlab.reservationslot.dto.ReservationSlotDeleteRequest;
import com.toy.reservationlab.reservationslot.dto.ReservationSlotResponse;
import com.toy.reservationlab.reservationslot.dto.ReservationSlotUpdateRequest;
import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
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
@RequestMapping("/reservation-slots")
@RequiredArgsConstructor
public class ReservationSlotController {

    private final ReservationSlotService reservationSlotService;

    @PostMapping
    public ApiResponse<ReservationSlotResponse> createReservationSlot(
            @RequestBody ReservationSlotCreateRequest request
    ) {
        ReservationSlot slot = reservationSlotService.createReservationSlot(
                request.slotId(),
                request.restaurantId(),
                request.slotDate(),
                request.slotTime(),
                request.capacity(),
                request.status(),
                request.createdBy()
        );
        return ApiResponse.success(ReservationSlotResponse.from(slot));
    }

    @GetMapping("/{slotId}")
    public ApiResponse<ReservationSlotResponse> getReservationSlot(@PathVariable String slotId) {
        ReservationSlot slot = reservationSlotService.getReservationSlot(slotId);
        return ApiResponse.success(ReservationSlotResponse.from(slot));
    }

    @PutMapping("/{slotId}")
    public ApiResponse<ReservationSlotResponse> updateReservationSlot(
            @PathVariable String slotId,
            @RequestBody ReservationSlotUpdateRequest request
    ) {
        ReservationSlot slot = reservationSlotService.updateReservationSlot(
                slotId,
                request.restaurantId(),
                request.slotDate(),
                request.slotTime(),
                request.capacity(),
                request.status(),
                request.updatedBy()
        );
        return ApiResponse.success(ReservationSlotResponse.from(slot));
    }

    @DeleteMapping("/{slotId}")
    public ApiResponse<ReservationSlotResponse> deleteReservationSlot(
            @PathVariable String slotId,
            @RequestBody ReservationSlotDeleteRequest request
    ) {
        ReservationSlot slot = reservationSlotService.deleteReservationSlot(slotId, request.updatedBy());
        return ApiResponse.success(ReservationSlotResponse.from(slot));
    }
}
