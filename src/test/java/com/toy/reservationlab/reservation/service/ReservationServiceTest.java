package com.toy.reservationlab.reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import com.toy.reservationlab.user.service.UserService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReservationServiceTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserService userService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Autowired
    private ReservationSlotRepository reservationSlotRepository;

    @Test
    void 예약을_생성하면_확정_상태로_저장되고_수용_인원이_차면_슬롯이_마감된다() {
        createUser("reservation-service-user-1", "010-3000-0001");
        createSlot("reservation-service-restaurant-1", "reservation-service-slot-1", 2);

        Reservation reservation = reservationService.createReservation(
                "reservation-service-1",
                "reservation-service-slot-1",
                "reservation-service-user-1",
                2,
                "reservation-service-user-1"
        );

        ReservationSlot slot = reservationSlotRepository.findById("reservation-service-slot-1").orElseThrow();
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertEquals(2, slot.getCapacity());
        assertEquals(ReservationSlotStatus.FULL, slot.getStatus());
    }

    @Test
    void 활성_예약_인원_합계가_슬롯_수용_인원을_초과하면_예약을_생성할_수_없다() {
        createUser("reservation-service-user-2", "010-3000-0002");
        createUser("reservation-service-user-3", "010-3000-0003");
        createSlot("reservation-service-restaurant-2", "reservation-service-slot-2", 3);
        reservationService.createReservation(
                "reservation-service-2",
                "reservation-service-slot-2",
                "reservation-service-user-2",
                2,
                "reservation-service-user-2"
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationService.createReservation(
                        "reservation-service-3",
                        "reservation-service-slot-2",
                        "reservation-service-user-3",
                        2,
                        "reservation-service-user-3"
                )
        );

        assertEquals("RSV00005", exception.getCode());
    }

    @Test
    void 확정_예약을_취소하면_마감된_슬롯이_예약_가능으로_복구된다() {
        createUser("reservation-service-user-4", "010-3000-0004");
        createSlot("reservation-service-restaurant-3", "reservation-service-slot-3", 2);
        reservationService.createReservation(
                "reservation-service-4",
                "reservation-service-slot-3",
                "reservation-service-user-4",
                2,
                "reservation-service-user-4"
        );

        Reservation reservation = reservationService.updateReservationStatus(
                "reservation-service-4",
                ReservationStatus.CANCELLED,
                "system"
        );

        ReservationSlot slot = reservationSlotRepository.findById("reservation-service-slot-3").orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals(ReservationSlotStatus.AVAILABLE, slot.getStatus());
    }

    @Test
    void 확정_예약을_노쇼로_변경해도_마감된_슬롯은_복구되지_않는다() {
        createUser("reservation-service-user-5", "010-3000-0005");
        createSlot("reservation-service-restaurant-4", "reservation-service-slot-4", 2);
        reservationService.createReservation(
                "reservation-service-5",
                "reservation-service-slot-4",
                "reservation-service-user-5",
                2,
                "reservation-service-user-5"
        );

        Reservation reservation = reservationService.updateReservationStatus(
                "reservation-service-5",
                ReservationStatus.NO_SHOW,
                "system"
        );

        ReservationSlot slot = reservationSlotRepository.findById("reservation-service-slot-4").orElseThrow();
        assertEquals(ReservationStatus.NO_SHOW, reservation.getStatus());
        assertEquals(ReservationSlotStatus.FULL, slot.getStatus());
    }

    @Test
    void 이미_취소된_예약은_다른_상태로_변경할_수_없다() {
        createUser("reservation-service-user-6", "010-3000-0006");
        createSlot("reservation-service-restaurant-5", "reservation-service-slot-5", 3);
        reservationService.createReservation(
                "reservation-service-6",
                "reservation-service-slot-5",
                "reservation-service-user-6",
                2,
                "reservation-service-user-6"
        );
        reservationService.updateReservationStatus("reservation-service-6", ReservationStatus.CANCELLED, "system");

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationService.updateReservationStatus(
                        "reservation-service-6",
                        ReservationStatus.NO_SHOW,
                        "system"
                )
        );

        assertEquals("RSV00006", exception.getCode());
    }

    @Test
    void 확정_예약을_삭제하면_취소_처리하고_소프트_삭제한다() {
        createUser("reservation-service-user-7", "010-3000-0007");
        createSlot("reservation-service-restaurant-6", "reservation-service-slot-6", 2);
        reservationService.createReservation(
                "reservation-service-7",
                "reservation-service-slot-6",
                "reservation-service-user-7",
                2,
                "reservation-service-user-7"
        );

        Reservation reservation = reservationService.deleteReservation("reservation-service-7", "system");

        ReservationSlot slot = reservationSlotRepository.findById("reservation-service-slot-6").orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertTrue(reservation.isDeleted());
        assertEquals(ReservationSlotStatus.AVAILABLE, slot.getStatus());
    }

    @Test
    void 삭제된_사용자로는_예약을_생성할_수_없다() {
        createUser("reservation-service-user-8", "010-3000-0008");
        userService.deleteUser("reservation-service-user-8", "system");
        createSlot("reservation-service-restaurant-7", "reservation-service-slot-7", 2);

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationService.createReservation(
                        "reservation-service-8",
                        "reservation-service-slot-7",
                        "reservation-service-user-8",
                        1,
                        "reservation-service-user-8"
                )
        );

        assertEquals("RSV00002", exception.getCode());
    }

    private void createUser(String userId, String phone) {
        userService.createUser(userId, "예약 사용자", phone, "system");
    }

    private void createSlot(String restaurantId, String slotId, int capacity) {
        restaurantService.createRestaurant(
                restaurantId,
                "예약 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "system"
        );
        reservationSlotService.createReservationSlot(
                slotId,
                restaurantId,
                LocalDate.now().plusDays(1),
                "18:00",
                capacity,
                ReservationSlotStatus.AVAILABLE,
                "system"
        );
    }
}
