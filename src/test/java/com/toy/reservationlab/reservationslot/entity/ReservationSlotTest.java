package com.toy.reservationlab.reservationslot.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReservationSlotTest {

    @Test
    void 예약가능한_슬롯은_예약을_생성할_수_있다() {
        ReservationSlot slot = ReservationSlot.create(
                "slot-1",
                "restaurant-1",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );

        assertTrue(slot.canCreateReservation());
    }

    @Test
    void 마감되거나_닫힌_슬롯은_예약을_생성할_수_없다() {
        ReservationSlot fullSlot = ReservationSlot.create(
                "slot-1",
                "restaurant-1",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.FULL,
                "user-1"
        );
        ReservationSlot closedSlot = ReservationSlot.create(
                "slot-2",
                "restaurant-1",
                LocalDate.now().plusDays(1),
                "19:00",
                10,
                ReservationSlotStatus.CLOSED,
                "user-1"
        );

        assertFalse(fullSlot.canCreateReservation());
        assertFalse(closedSlot.canCreateReservation());
    }

    @Test
    void 삭제된_슬롯은_예약을_생성할_수_없다() {
        ReservationSlot slot = ReservationSlot.create(
                "slot-1",
                "restaurant-1",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );

        slot.markDeleted("user-2");

        assertTrue(slot.isDeleted());
        assertFalse(slot.canCreateReservation());
        assertEquals("user-2", slot.getUpdatedBy());
    }

    @Test
    void 슬롯_상태는_정의된_값만_사용한다() {
        assertEquals(3, ReservationSlotStatus.values().length);
        assertEquals(ReservationSlotStatus.AVAILABLE, ReservationSlotStatus.valueOf("AVAILABLE"));
        assertEquals(ReservationSlotStatus.FULL, ReservationSlotStatus.valueOf("FULL"));
        assertEquals(ReservationSlotStatus.CLOSED, ReservationSlotStatus.valueOf("CLOSED"));
    }
}
