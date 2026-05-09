package com.toy.reservationlab.reservationslot.service;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationSlotService {

    private static final String NOT_DELETED = "N";
    private static final String RESERVATION_SLOT_NOT_FOUND = "RSL00001";
    private static final String CANNOT_CREATE_RESERVATION_SLOT = "RSL00002";
    private static final String PAST_SLOT_DATE = "RSL00003";
    private static final String DUPLICATE_RESERVATION_SLOT = "RSL00004";
    private static final String CANNOT_REDUCE_FULL_SLOT_CAPACITY = "RSL00005";
    private static final String CONFIRMED_RESERVATION_EXISTS = "RSL00006";

    private final ReservationSlotRepository reservationSlotRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantService restaurantService;

    @Transactional
    public ReservationSlot createReservationSlot(
            String slotId,
            String restaurantId,
            LocalDate slotDate,
            String slotTime,
            int capacity,
            ReservationSlotStatus status,
            String createdBy
    ) {
        validateCreatableRestaurant(restaurantId);
        validateNotPast(slotDate);
        validateNotDuplicate(restaurantId, slotDate, slotTime);

        ReservationSlot slot = ReservationSlot.create(
                slotId,
                restaurantId,
                slotDate,
                slotTime,
                capacity,
                status,
                createdBy
        );
        return reservationSlotRepository.save(slot);
    }

    public ReservationSlot getReservationSlot(String slotId) {
        return findReservationSlot(slotId);
    }

    @Transactional
    public ReservationSlot updateReservationSlot(
            String slotId,
            String restaurantId,
            LocalDate slotDate,
            String slotTime,
            int capacity,
            ReservationSlotStatus status,
            String updatedBy
    ) {
        ReservationSlot slot = findReservationSlot(slotId);
        validateCreatableRestaurant(restaurantId);
        validateNotPast(slotDate);
        validateNotDuplicate(restaurantId, slotDate, slotTime, slotId);
        validateCapacityChange(slot, capacity);

        slot.update(restaurantId, slotDate, slotTime, capacity, status, updatedBy);
        return slot;
    }

    @Transactional
    public ReservationSlot deleteReservationSlot(String slotId, String updatedBy) {
        ReservationSlot slot = findReservationSlot(slotId);
        if (hasConfirmedReservation(slotId)) {
            throw new BizException(CONFIRMED_RESERVATION_EXISTS);
        }

        slot.markDeleted(updatedBy);
        return slot;
    }

    private void validateCreatableRestaurant(String restaurantId) {
        if (!restaurantService.canCreateReservationSlot(restaurantId)) {
            throw new BizException(CANNOT_CREATE_RESERVATION_SLOT);
        }
    }

    private void validateNotPast(LocalDate slotDate) {
        if (slotDate.isBefore(LocalDate.now())) {
            throw new BizException(PAST_SLOT_DATE);
        }
    }

    private void validateNotDuplicate(String restaurantId, LocalDate slotDate, String slotTime) {
        if (reservationSlotRepository.existsByRestaurantIdAndSlotDateAndSlotTimeAndDelYn(
                restaurantId,
                slotDate,
                slotTime,
                NOT_DELETED
        )) {
            throw new BizException(DUPLICATE_RESERVATION_SLOT);
        }
    }

    private void validateNotDuplicate(String restaurantId, LocalDate slotDate, String slotTime, String slotId) {
        if (reservationSlotRepository.countActiveDuplicateSlot(restaurantId, slotDate, slotTime, slotId) > 0) {
            throw new BizException(DUPLICATE_RESERVATION_SLOT);
        }
    }

    private void validateCapacityChange(ReservationSlot slot, int capacity) {
        if (slot.isFull() && slot.isReducingCapacity(capacity)) {
            throw new BizException(CANNOT_REDUCE_FULL_SLOT_CAPACITY);
        }
    }

    private boolean hasConfirmedReservation(String slotId) {
        return reservationRepository.countBySlotIdAndStatusAndDelYn(
                slotId,
                ReservationStatus.CONFIRMED,
                NOT_DELETED
        ) > 0;
    }

    private ReservationSlot findReservationSlot(String slotId) {
        return reservationSlotRepository.findById(slotId)
                .orElseThrow(() -> new BizException(RESERVATION_SLOT_NOT_FOUND));
    }
}
