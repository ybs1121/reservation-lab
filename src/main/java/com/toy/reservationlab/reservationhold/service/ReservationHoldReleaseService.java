package com.toy.reservationlab.reservationhold.service;

import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_HOLD_NOT_FOUND;
import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_HOLD_OWNER_MISMATCH;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.common.component.DistributedLock;
import com.toy.reservationlab.reservationhold.component.ReservationHoldData;
import com.toy.reservationlab.reservationhold.component.ReservationHoldStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservation-lab.reservation-hold.enabled", havingValue = "true")
public class ReservationHoldReleaseService {

    private final ReservationHoldStore reservationHoldStore;

    // 확정과 같은 락을 사용해 하나의 hold가 확정과 해제에 동시에 성공하지 않게 한다.
    @DistributedLock(keys = {
            "'lock:reservation-hold:user:' + #hold.userId()",
            "'lock:reservation:slot:' + #hold.slotId()"
    })
    public void release(ReservationHoldData hold, String userId) {
        ReservationHoldData currentHold = reservationHoldStore.find(hold.holdId())
                .orElseThrow(() -> new BizException(RESERVATION_HOLD_NOT_FOUND));
        validateOwner(currentHold, userId);
        reservationHoldStore.delete(currentHold);
    }

    private void validateOwner(ReservationHoldData hold, String userId) {
        if (!hold.userId().equals(userId)) {
            throw new BizException(RESERVATION_HOLD_OWNER_MISMATCH);
        }
    }
}
