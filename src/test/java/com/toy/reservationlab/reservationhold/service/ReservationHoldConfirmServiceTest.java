package com.toy.reservationlab.reservationhold.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.service.ReservationService;
import com.toy.reservationlab.reservationhold.component.ReservationHoldData;
import com.toy.reservationlab.reservationhold.component.ReservationHoldStore;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldConfirmRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationHoldConfirmServiceTest {

    @Mock
    private ReservationHoldStore reservationHoldStore;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationHoldConfirmService reservationHoldConfirmService;

    @Test
    void 확정_처리_직전에_TTL이_만료되면_예약을_생성하지_않는다() {
        ReservationHoldData expiredDuringConfirm = new ReservationHoldData(
                "race-hold-1",
                "race-slot-1",
                "race-user-1",
                1
        );
        ReservationHoldConfirmRequest request = new ReservationHoldConfirmRequest(
                "race-reservation-1",
                "race-user-1",
                "race-user-1"
        );
        when(reservationHoldStore.find("race-hold-1")).thenReturn(Optional.empty());

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationHoldConfirmService.confirm(expiredDuringConfirm, request)
        );

        assertEquals("RSH00001", exception.getCode());
        verify(reservationService, never()).createReservation(
                "race-reservation-1",
                "race-slot-1",
                "race-user-1",
                1,
                "race-user-1"
        );
        verify(reservationHoldStore, never()).delete(expiredDuringConfirm);
    }
}
