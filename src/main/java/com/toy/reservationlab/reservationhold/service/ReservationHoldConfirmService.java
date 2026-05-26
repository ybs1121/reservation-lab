package com.toy.reservationlab.reservationhold.service;

import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_HOLD_NOT_FOUND;
import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_HOLD_OWNER_MISMATCH;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.common.component.DistributedLock;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.service.ReservationService;
import com.toy.reservationlab.reservationhold.component.ReservationHoldData;
import com.toy.reservationlab.reservationhold.component.ReservationHoldStore;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldConfirmRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservation-lab.reservation-hold.enabled", havingValue = "true")
public class ReservationHoldConfirmService {

    private final ReservationHoldStore reservationHoldStore;
    private final ReservationService reservationService;

    // hold가 만료되지 않았고 소유자가 맞을 때만 확정 예약을 생성한 뒤 hold를 제거한다.
    @DistributedLock(key = "'lock:reservation:slot:' + #hold.slotId()")
    public Reservation confirm(ReservationHoldData hold, ReservationHoldConfirmRequest request) {
        ReservationHoldData currentHold = reservationHoldStore.find(hold.holdId())
                .orElseThrow(() -> new BizException(RESERVATION_HOLD_NOT_FOUND));
        validateOwner(currentHold, request.userId());

        Reservation reservation = reservationService.createReservation(
                request.reservationId(),
                currentHold.slotId(),
                currentHold.userId(),
                currentHold.partySize(),
                request.createdBy()
        );
        reservationHoldStore.delete(currentHold);
        return reservation;
    }

    private void validateOwner(ReservationHoldData hold, String userId) {
        if (!hold.userId().equals(userId)) {
            throw new BizException(RESERVATION_HOLD_OWNER_MISMATCH);
        }
    }
}
