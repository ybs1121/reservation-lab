package com.toy.reservationlab.reservationhold.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldConfirmRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldCreateRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldResponse;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import com.toy.reservationlab.user.service.UserService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "reservation-lab.distributed-lock.enabled=true",
        "reservation-lab.reservation-hold.enabled=true",
        "reservation-lab.reservation-hold.ttl-seconds=1",
        "reservation-lab.reservation-hold.user-active-hold-max-count=3"
})
class ReservationHoldServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ReservationHoldService reservationHoldService;

    @Autowired
    private UserService userService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Test
    void 임시_점유를_생성하면_TTL과_함께_반환한다() {
        createUser("hold-service-user-1", "010-5100-0001");
        createSlot("hold-service-restaurant-1", "hold-service-slot-1", 2);

        ReservationHoldResponse response = reservationHoldService.createHold(
                new ReservationHoldCreateRequest("hold-service-slot-1", "hold-service-user-1", 1)
        );

        assertEquals("hold-service-slot-1", response.slotId());
        assertEquals("hold-service-user-1", response.userId());
        assertEquals(1, response.partySize());
        assertTrue(response.ttlSeconds() <= 1);
    }

    @Test
    void 같은_사용자와_슬롯의_임시_점유는_기존_hold를_반환하고_TTL을_연장하지_않는다() throws InterruptedException {
        createUser("hold-service-user-2", "010-5100-0002");
        createSlot("hold-service-restaurant-2", "hold-service-slot-2", 2);
        ReservationHoldCreateRequest request = new ReservationHoldCreateRequest(
                "hold-service-slot-2",
                "hold-service-user-2",
                1
        );

        ReservationHoldResponse first = reservationHoldService.createHold(request);
        Thread.sleep(200);
        ReservationHoldResponse second = reservationHoldService.createHold(request);

        assertEquals(first.holdId(), second.holdId());
        assertTrue(second.ttlSeconds() <= first.ttlSeconds());
    }

    @Test
    void 임시_점유_인원을_포함해_슬롯_수용_인원을_초과하면_실패한다() {
        createUser("hold-service-user-3", "010-5100-0003");
        createUser("hold-service-user-4", "010-5100-0004");
        createSlot("hold-service-restaurant-3", "hold-service-slot-3", 1);
        reservationHoldService.createHold(
                new ReservationHoldCreateRequest("hold-service-slot-3", "hold-service-user-3", 1)
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationHoldService.createHold(
                        new ReservationHoldCreateRequest("hold-service-slot-3", "hold-service-user-4", 1)
                )
        );

        assertEquals("RSV00005", exception.getCode());
    }

    @Test
    void 사용자는_active_hold를_최대_3개까지만_가질_수_있다() {
        createUser("hold-service-user-5", "010-5100-0005");
        createSlot("hold-service-restaurant-4", "hold-service-slot-4", 1);
        createSlot("hold-service-restaurant-5", "hold-service-slot-5", 1);
        createSlot("hold-service-restaurant-6", "hold-service-slot-6", 1);
        createSlot("hold-service-restaurant-7", "hold-service-slot-7", 1);

        reservationHoldService.createHold(new ReservationHoldCreateRequest("hold-service-slot-4", "hold-service-user-5", 1));
        reservationHoldService.createHold(new ReservationHoldCreateRequest("hold-service-slot-5", "hold-service-user-5", 1));
        reservationHoldService.createHold(new ReservationHoldCreateRequest("hold-service-slot-6", "hold-service-user-5", 1));

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationHoldService.createHold(
                        new ReservationHoldCreateRequest("hold-service-slot-7", "hold-service-user-5", 1)
                )
        );

        assertEquals("RSH00002", exception.getCode());
    }

    @Test
    void 유효한_hold를_확정하면_예약이_생성되고_hold는_삭제된다() {
        createUser("hold-service-user-6", "010-5100-0006");
        createSlot("hold-service-restaurant-8", "hold-service-slot-8", 1);
        ReservationHoldResponse hold = reservationHoldService.createHold(
                new ReservationHoldCreateRequest("hold-service-slot-8", "hold-service-user-6", 1)
        );

        Reservation reservation = reservationHoldService.confirmHold(
                hold.holdId(),
                new ReservationHoldConfirmRequest(
                        "hold-service-reservation-1",
                        "hold-service-user-6",
                        "hold-service-user-6"
                )
        );

        assertEquals("hold-service-reservation-1", reservation.getReservationId());
        BizException exception = assertThrows(
                BizException.class,
                () -> reservationHoldService.getHold(hold.holdId())
        );
        assertEquals("RSH00001", exception.getCode());
    }

    @Test
    void hold_소유자가_아니면_확정할_수_없다() {
        createUser("hold-service-user-7", "010-5100-0007");
        createUser("hold-service-user-8", "010-5100-0008");
        createSlot("hold-service-restaurant-9", "hold-service-slot-9", 1);
        ReservationHoldResponse hold = reservationHoldService.createHold(
                new ReservationHoldCreateRequest("hold-service-slot-9", "hold-service-user-7", 1)
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationHoldService.confirmHold(
                        hold.holdId(),
                        new ReservationHoldConfirmRequest(
                                "hold-service-reservation-2",
                                "hold-service-user-8",
                                "hold-service-user-8"
                        )
                )
        );

        assertEquals("RSH00003", exception.getCode());
    }

    @Test
    void TTL이_만료된_hold는_확정할_수_없고_다시_점유할_수_있다() throws InterruptedException {
        createUser("hold-service-user-9", "010-5100-0009");
        createUser("hold-service-user-10", "010-5100-0010");
        createSlot("hold-service-restaurant-10", "hold-service-slot-10", 1);
        ReservationHoldResponse expiredHold = reservationHoldService.createHold(
                new ReservationHoldCreateRequest("hold-service-slot-10", "hold-service-user-9", 1)
        );

        Thread.sleep(1300);

        BizException exception = assertThrows(
                BizException.class,
                () -> reservationHoldService.confirmHold(
                        expiredHold.holdId(),
                        new ReservationHoldConfirmRequest(
                                "hold-service-reservation-3",
                                "hold-service-user-9",
                                "hold-service-user-9"
                        )
                )
        );
        ReservationHoldResponse newHold = reservationHoldService.createHold(
                new ReservationHoldCreateRequest("hold-service-slot-10", "hold-service-user-10", 1)
        );

        assertEquals("RSH00001", exception.getCode());
        assertNotEquals(expiredHold.holdId(), newHold.holdId());
    }

    private void createUser(String userId, String phone) {
        userService.createUser(userId, "임시 점유 사용자", phone, "system");
    }

    private void createSlot(String restaurantId, String slotId, int capacity) {
        restaurantService.createRestaurant(
                restaurantId,
                "임시 점유 식당",
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
