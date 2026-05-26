package com.toy.reservationlab.reservation.service;

import static com.toy.reservationlab.common.component.ErrorCode.CANNOT_CREATE_RESERVATION_TARGET_SLOT;
import static com.toy.reservationlab.common.component.ErrorCode.CANNOT_CREATE_RESERVATION_USER;
import static com.toy.reservationlab.common.component.ErrorCode.CAPACITY_EXCEEDED;
import static com.toy.reservationlab.common.component.ErrorCode.INVALID_PARTY_SIZE;
import static com.toy.reservationlab.common.component.ErrorCode.INVALID_RESERVATION_STATUS_TRANSITION;
import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_NOT_FOUND;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.common.component.DistributedLock;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.restaurant.component.PopularRestaurantCacheEvictor;
import com.toy.reservationlab.user.entity.User;
import com.toy.reservationlab.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private static final List<ReservationStatus> ACTIVE_PARTY_SIZE_STATUSES = List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.NO_SHOW
    );

    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository reservationSlotRepository;
    private final UserRepository userRepository;
    private final PopularRestaurantCacheEvictor popularRestaurantCacheEvictor;

    @Transactional
    @DistributedLock(key = "'lock:reservation:slot:' + #slotId")
    public Reservation createReservation(
            String reservationId,
            String slotId,
            String userId,
            int partySize,
            String createdBy
    ) {
        validateCreatableUser(userId);
        ReservationSlot slot = findCreatableSlot(slotId);
        validatePartySize(partySize);

        long activePartySize = getActivePartySize(slotId);
        if (!slot.hasCapacityFor((int) activePartySize, partySize)) {
            throw new BizException(CAPACITY_EXCEEDED);
        }

        Reservation reservation = Reservation.create(
                reservationId,
                slotId,
                userId,
                partySize,
                ReservationStatus.CONFIRMED,
                createdBy
        );
        Reservation savedReservation = reservationRepository.save(reservation);
        slot.markFullIfCapacityReached((int) activePartySize + partySize, createdBy);
        // 예약 생성은 전체/최근 인기 순위를 바꿀 수 있어 인기 캐시를 무효화한다.
        popularRestaurantCacheEvictor.evictAll();
        return savedReservation;
    }

    public Reservation getReservation(String reservationId) {
        return findReservation(reservationId);
    }

    @Transactional
    public Reservation updateReservationStatus(
            String reservationId,
            ReservationStatus status,
            String updatedBy
    ) {
        Reservation reservation = findReservation(reservationId);
        if (!reservation.canChangeStatusTo(status)) {
            throw new BizException(INVALID_RESERVATION_STATUS_TRANSITION);
        }

        if (status == ReservationStatus.CANCELLED) {
            cancelReservation(reservation, updatedBy);
            // 인기 기준에서 빠지는 상태 전환이라 캐시된 순위를 다시 계산해야 한다.
            popularRestaurantCacheEvictor.evictAll();
            return reservation;
        }

        reservation.changeStatus(status, updatedBy);
        // NO_SHOW도 인기 기준에 포함하지만, 상태 정책 변경 가능성을 고려해 쓰기 시점에 비운다.
        popularRestaurantCacheEvictor.evictAll();
        return reservation;
    }

    @Transactional
    public Reservation deleteReservation(String reservationId, String updatedBy) {
        Reservation reservation = findReservation(reservationId);
        if (reservation.isConfirmed()) {
            cancelReservation(reservation, updatedBy);
        }
        reservation.markDeleted(updatedBy);
        // 삭제된 예약은 인기 계산에서 제외되므로 캐시를 무효화한다.
        popularRestaurantCacheEvictor.evictAll();
        return reservation;
    }

    private void cancelReservation(Reservation reservation, String updatedBy) {
        long activePartySize = getActivePartySize(reservation.getSlotId());
        reservation.cancel(updatedBy);
        restoreSlotAvailableIfNeeded(
                reservation.getSlotId(),
                activePartySize - reservation.getPartySize(),
                updatedBy
        );
    }

    private void restoreSlotAvailableIfNeeded(String slotId, long activePartySize, String updatedBy) {
        reservationSlotRepository.findById(slotId)
                .ifPresent(slot -> slot.restoreAvailableIfNotFull((int) activePartySize, updatedBy));
    }

    private void validateCreatableUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(CANNOT_CREATE_RESERVATION_USER));
        if (user.isDeleted()) {
            throw new BizException(CANNOT_CREATE_RESERVATION_USER);
        }
    }

    private ReservationSlot findCreatableSlot(String slotId) {
        ReservationSlot slot = reservationSlotRepository.findById(slotId)
                .orElseThrow(() -> new BizException(CANNOT_CREATE_RESERVATION_TARGET_SLOT));
        if (!slot.canCreateReservation()) {
            throw new BizException(CANNOT_CREATE_RESERVATION_TARGET_SLOT);
        }
        return slot;
    }

    private void validatePartySize(int partySize) {
        if (partySize < 1) {
            throw new BizException(INVALID_PARTY_SIZE);
        }
    }

    private long getActivePartySize(String slotId) {
        return reservationRepository.sumPartySizeBySlotIdAndStatuses(slotId, ACTIVE_PARTY_SIZE_STATUSES);
    }

    private Reservation findReservation(String reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BizException(RESERVATION_NOT_FOUND));
    }
}
