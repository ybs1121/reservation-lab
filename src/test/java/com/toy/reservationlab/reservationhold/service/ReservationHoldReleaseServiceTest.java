package com.toy.reservationlab.reservationhold.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservationhold.component.ReservationHoldData;
import com.toy.reservationlab.reservationhold.component.ReservationHoldStore;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationHoldReleaseServiceTest {

    @Mock
    private ReservationHoldStore reservationHoldStore;

    @InjectMocks
    private ReservationHoldReleaseService reservationHoldReleaseService;

    @Test
    void 락을_기다리는_동안_hold가_삭제되면_해제에_실패한다() {
        ReservationHoldData deletedHold = hold();
        when(reservationHoldStore.find(deletedHold.holdId())).thenReturn(Optional.empty());

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationHoldReleaseService.release(deletedHold, deletedHold.userId())
        );

        assertEquals("RSH00001", exception.getCode());
        verify(reservationHoldStore, never()).delete(deletedHold);
    }

    @Test
    void hold_소유자가_아니면_해제할_수_없다() {
        ReservationHoldData hold = hold();
        when(reservationHoldStore.find(hold.holdId())).thenReturn(Optional.of(hold));

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationHoldReleaseService.release(hold, "other-user")
        );

        assertEquals("RSH00003", exception.getCode());
        verify(reservationHoldStore, never()).delete(hold);
    }

    private ReservationHoldData hold() {
        return new ReservationHoldData("release-hold", "release-slot", "release-user", 1);
    }
}
