package com.toy.reservationlab.reservationslot.service;

import static com.toy.reservationlab.common.component.ErrorCode.CANNOT_CREATE_RESERVATION_SLOT;
import static com.toy.reservationlab.common.component.ErrorCode.CANNOT_REDUCE_FULL_SLOT_CAPACITY;
import static com.toy.reservationlab.common.component.ErrorCode.CONFIRMED_RESERVATION_EXISTS;
import static com.toy.reservationlab.common.component.ErrorCode.DUPLICATE_RESERVATION_SLOT;
import static com.toy.reservationlab.common.component.ErrorCode.PAST_SLOT_DATE;
import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_SLOT_NOT_FOUND;
import static com.toy.reservationlab.common.component.ErrorCode.RESTAURANT_NOT_FOUND;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.restaurant.entity.Restaurant;
import com.toy.reservationlab.restaurant.repository.RestaurantRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationSlotService {

    private static final String NOT_DELETED = "N";

    private final ReservationSlotRepository reservationSlotRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantRepository restaurantRepository;

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
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new BizException(RESTAURANT_NOT_FOUND));
        if (!restaurant.canCreateReservationSlot()) {
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
