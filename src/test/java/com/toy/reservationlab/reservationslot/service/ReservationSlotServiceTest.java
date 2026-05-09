package com.toy.reservationlab.reservationslot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReservationSlotServiceTest {

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void 예약슬롯을_생성하면_저장된다() {
        restaurantService.createRestaurant(
                "restaurant-slot-service-1",
                "슬롯 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        ReservationSlot slot = reservationSlotService.createReservationSlot(
                "slot-service-create-1",
                "restaurant-slot-service-1",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );

        assertEquals("slot-service-create-1", slot.getSlotId());
        assertEquals("restaurant-slot-service-1", slot.getRestaurantId());
        assertEquals("N", slot.getDelYn());
    }

    @Test
    void 같은_식당_날짜_시간의_예약슬롯은_중복_생성할_수_없다() {
        restaurantService.createRestaurant(
                "restaurant-slot-service-2",
                "중복 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        LocalDate slotDate = LocalDate.now().plusDays(1);
        reservationSlotService.createReservationSlot(
                "slot-service-duplicate-1",
                "restaurant-slot-service-2",
                slotDate,
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationSlotService.createReservationSlot(
                        "slot-service-duplicate-2",
                        "restaurant-slot-service-2",
                        slotDate,
                        "18:00",
                        10,
                        ReservationSlotStatus.AVAILABLE,
                        "user-1"
                )
        );

        assertEquals("RSL00004", exception.getCode());
    }

    @Test
    void 닫힌_식당에는_예약슬롯을_생성할_수_없다() {
        restaurantService.createRestaurant(
                "restaurant-slot-service-3",
                "닫힌 식당",
                "서울시 강남구",
                RestaurantStatus.CLOSED,
                "user-1"
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationSlotService.createReservationSlot(
                        "slot-service-closed-1",
                        "restaurant-slot-service-3",
                        LocalDate.now().plusDays(1),
                        "18:00",
                        10,
                        ReservationSlotStatus.AVAILABLE,
                        "user-1"
                )
        );

        assertEquals("RSL00002", exception.getCode());
    }

    @Test
    void 예약슬롯을_수정하면_변경된_값이_반영된다() {
        restaurantService.createRestaurant(
                "restaurant-slot-service-4",
                "수정 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        reservationSlotService.createReservationSlot(
                "slot-service-update-1",
                "restaurant-slot-service-4",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );

        ReservationSlot slot = reservationSlotService.updateReservationSlot(
                "slot-service-update-1",
                "restaurant-slot-service-4",
                LocalDate.now().plusDays(2),
                "19:00",
                12,
                ReservationSlotStatus.CLOSED,
                "user-2"
        );

        assertEquals(LocalDate.now().plusDays(2), slot.getSlotDate());
        assertEquals("19:00", slot.getSlotTime());
        assertEquals(12, slot.getCapacity());
        assertEquals(ReservationSlotStatus.CLOSED, slot.getStatus());
        assertEquals("user-2", slot.getUpdatedBy());
    }

    @Test
    void 마감된_예약슬롯은_최대_수용_인원을_줄일_수_없다() {
        restaurantService.createRestaurant(
                "restaurant-slot-service-5",
                "마감 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        reservationSlotService.createReservationSlot(
                "slot-service-full-1",
                "restaurant-slot-service-5",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.FULL,
                "user-1"
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationSlotService.updateReservationSlot(
                        "slot-service-full-1",
                        "restaurant-slot-service-5",
                        LocalDate.now().plusDays(1),
                        "18:00",
                        9,
                        ReservationSlotStatus.FULL,
                        "user-2"
                )
        );

        assertEquals("RSL00005", exception.getCode());
    }

    @Test
    void 확정된_예약이_없으면_예약슬롯을_삭제할_수_있다() {
        restaurantService.createRestaurant(
                "restaurant-slot-service-6",
                "삭제 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        reservationSlotService.createReservationSlot(
                "slot-service-delete-1",
                "restaurant-slot-service-6",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );

        ReservationSlot slot = reservationSlotService.deleteReservationSlot("slot-service-delete-1", "user-2");

        assertTrue(slot.isDeleted());
        assertEquals("user-2", slot.getUpdatedBy());
    }

    @Test
    void 확정된_예약이_있으면_예약슬롯을_삭제할_수_없다() {
        restaurantService.createRestaurant(
                "restaurant-slot-service-7",
                "예약 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        reservationSlotService.createReservationSlot(
                "slot-service-confirmed-1",
                "restaurant-slot-service-7",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );
        reservationRepository.save(Reservation.create(
                "reservation-slot-service-1",
                "slot-service-confirmed-1",
                "user-1",
                2,
                ReservationStatus.CONFIRMED,
                "user-1"
        ));

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationSlotService.deleteReservationSlot("slot-service-confirmed-1", "user-2")
        );

        assertEquals("RSL00006", exception.getCode());
    }
}
