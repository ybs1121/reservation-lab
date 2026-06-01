package com.toy.reservationlab.reservationhold.service;

import static com.toy.reservationlab.common.component.ErrorCode.CANNOT_CREATE_RESERVATION_TARGET_SLOT;
import static com.toy.reservationlab.common.component.ErrorCode.CANNOT_CREATE_RESERVATION_USER;
import static com.toy.reservationlab.common.component.ErrorCode.CAPACITY_EXCEEDED;
import static com.toy.reservationlab.common.component.ErrorCode.INVALID_PARTY_SIZE;
import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_HOLD_LIMIT_EXCEEDED;
import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_HOLD_NOT_FOUND;
import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_HOLD_OWNER_MISMATCH;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.common.component.DistributedLock;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservationhold.component.ReservationHoldData;
import com.toy.reservationlab.reservationhold.component.ReservationHoldStore;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldConfirmRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldCreateRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldResponse;
import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.user.entity.User;
import com.toy.reservationlab.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "reservation-lab.reservation-hold.enabled", havingValue = "true")
public class ReservationHoldService {

    private static final List<ReservationStatus> ACTIVE_PARTY_SIZE_STATUSES = List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.NO_SHOW
    );

    private final ReservationHoldStore reservationHoldStore;
    private final ReservationHoldConfirmService reservationHoldConfirmService;
    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository reservationSlotRepository;
    private final UserRepository userRepository;

    @Value("${reservation-lab.reservation-hold.ttl-seconds:300}")
    private long ttlSeconds;

    @Value("${reservation-lab.reservation-hold.user-active-hold-max-count:3}")
    private int userActiveHoldMaxCount;

    // 슬롯 capacity를 임시 선점하고, 같은 사용자/슬롯 조합이면 기존 hold를 재사용한다.
    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    @DistributedLock(key = "'lock:reservation:slot:' + #request.slotId()")
    public ReservationHoldResponse createHold(ReservationHoldCreateRequest request) {
        validateCreatableUser(request.userId());
        ReservationSlot slot = findCreatableSlot(request.slotId());
        validatePartySize(request.partySize());

        return reservationHoldStore.findByUserIdAndSlotId(request.userId(), request.slotId())
                .map(this::toResponse)
                .orElseGet(() -> createNewHold(request, slot));
    }

    // 화면 복구나 남은 시간 표시에 필요한 hold 상태를 반환한다.
    public ReservationHoldResponse getHold(String holdId) {
        ReservationHoldData hold = findHold(holdId);
        return toResponse(hold);
    }

    // 유효한 hold를 확정 예약으로 전환한다.
    public Reservation confirmHold(String holdId, ReservationHoldConfirmRequest request) {
        ReservationHoldData hold = findHold(holdId);
        return reservationHoldConfirmService.confirm(hold, request);
    }

    // 사용자가 확정 전에 이탈하거나 취소할 때 임시 점유를 명시적으로 해제한다.
    public void releaseHold(String holdId, String userId) {
        ReservationHoldData hold = findHold(holdId);
        validateOwner(hold, userId);
        reservationHoldStore.delete(hold);
    }

    private ReservationHoldResponse createNewHold(ReservationHoldCreateRequest request, ReservationSlot slot) {
        validateUserHoldLimit(request.userId());
        validateCapacity(slot, request.partySize());

        ReservationHoldData hold = new ReservationHoldData(
                UUID.randomUUID().toString(),
                request.slotId(),
                request.userId(),
                request.partySize()
        );
        reservationHoldStore.save(hold, ttlSeconds);
        return toResponse(hold);
    }

    // 사용자별 active hold 남발을 막기 위한 최소 제한을 적용한다.
    private void validateUserHoldLimit(String userId) {
        if (reservationHoldStore.countByUserId(userId) >= userActiveHoldMaxCount) {
            throw new BizException(RESERVATION_HOLD_LIMIT_EXCEEDED);
        }
    }

    // 확정 예약 인원과 임시 점유 인원을 함께 보아 슬롯 capacity 초과를 막는다.
    private void validateCapacity(ReservationSlot slot, int partySize) {
        long activePartySize = reservationRepository.sumPartySizeBySlotIdAndStatuses(
                slot.getSlotId(),
                ACTIVE_PARTY_SIZE_STATUSES
        );
        int activeHoldPartySize = reservationHoldStore.sumPartySizeBySlotId(slot.getSlotId());
        if (!slot.hasCapacityFor((int) activePartySize + activeHoldPartySize, partySize)) {
            throw new BizException(CAPACITY_EXCEEDED);
        }
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

    private ReservationHoldData findHold(String holdId) {
        return reservationHoldStore.find(holdId)
                .orElseThrow(() -> new BizException(RESERVATION_HOLD_NOT_FOUND));
    }

    private void validateOwner(ReservationHoldData hold, String userId) {
        if (!hold.userId().equals(userId)) {
            throw new BizException(RESERVATION_HOLD_OWNER_MISMATCH);
        }
    }

    private ReservationHoldResponse toResponse(ReservationHoldData hold) {
        return ReservationHoldResponse.from(
                hold,
                reservationHoldStore.getTtlSeconds(hold.holdId())
        );
    }
}
