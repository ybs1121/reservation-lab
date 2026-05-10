package com.toy.reservationlab.reservation.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReservationTest {

    @Test
    void 예약을_생성하면_삭제되지_않은_상태다() {
        Reservation reservation = Reservation.create(
                "reservation-1",
                "slot-1",
                "user-1",
                2,
                ReservationStatus.CONFIRMED,
                "user-1"
        );

        assertEquals("reservation-1", reservation.getReservationId());
        assertEquals("N", reservation.getDelYn());
        assertFalse(reservation.isDeleted());
    }

    @Test
    void 예약을_삭제_표시할_수_있다() {
        Reservation reservation = Reservation.create(
                "reservation-1",
                "slot-1",
                "user-1",
                2,
                ReservationStatus.CONFIRMED,
                "user-1"
        );

        reservation.markDeleted("user-2");

        assertTrue(reservation.isDeleted());
        assertEquals("user-2", reservation.getUpdatedBy());
    }

    @Test
    void 예약_상태는_정의된_값만_사용한다() {
        assertEquals(3, ReservationStatus.values().length);
        assertEquals(ReservationStatus.CONFIRMED, ReservationStatus.valueOf("CONFIRMED"));
        assertEquals(ReservationStatus.CANCELLED, ReservationStatus.valueOf("CANCELLED"));
        assertEquals(ReservationStatus.NO_SHOW, ReservationStatus.valueOf("NO_SHOW"));
    }
}
