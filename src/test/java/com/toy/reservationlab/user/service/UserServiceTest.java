package com.toy.reservationlab.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void 사용자를_생성하면_저장된다() {
        User user = userService.createUser(
                "user-service-1",
                "테스트 사용자",
                "010-1000-0001",
                "system"
        );

        assertEquals("user-service-1", user.getUserId());
        assertEquals("테스트 사용자", user.getName());
        assertEquals("N", user.getDelYn());
    }

    @Test
    void 사용자를_조회하면_저장된_사용자를_반환한다() {
        userService.createUser(
                "user-service-2",
                "조회 사용자",
                "010-1000-0002",
                "system"
        );

        User user = userService.getUser("user-service-2");

        assertEquals("조회 사용자", user.getName());
        assertEquals("010-1000-0002", user.getPhone());
    }

    @Test
    void 존재하지_않는_사용자를_조회하면_비즈니스_예외가_발생한다() {
        BizException exception = assertThrows(
                BizException.class,
                () -> userService.getUser("not-found-user")
        );

        assertEquals("USR00001", exception.getCode());
    }

    @Test
    void 연락처가_중복되면_사용자를_생성할_수_없다() {
        userService.createUser(
                "user-service-3",
                "기존 사용자",
                "010-1000-0003",
                "system"
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> userService.createUser(
                        "user-service-4",
                        "중복 사용자",
                        "010-1000-0003",
                        "system"
                )
        );

        assertEquals("USR00002", exception.getCode());
    }

    @Test
    void 사용자를_수정하면_변경된_값이_반영된다() {
        userService.createUser(
                "user-service-5",
                "수정 전 사용자",
                "010-1000-0005",
                "system"
        );

        User user = userService.updateUser(
                "user-service-5",
                "수정 후 사용자",
                "010-1000-5005",
                "user-1"
        );

        assertEquals("수정 후 사용자", user.getName());
        assertEquals("010-1000-5005", user.getPhone());
        assertEquals("user-1", user.getUpdatedBy());
    }

    @Test
    void 사용자를_삭제하면_삭제_표시된다() {
        userService.createUser(
                "user-service-6",
                "삭제 사용자",
                "010-1000-0006",
                "system"
        );

        User user = userService.deleteUser("user-service-6", "user-1");

        assertTrue(user.isDeleted());
        assertEquals("user-1", user.getUpdatedBy());
    }

    @Test
    void 사용자를_삭제하면_확정된_예약은_취소된다() {
        userService.createUser(
                "user-service-7",
                "예약 사용자",
                "010-1000-0007",
                "system"
        );
        reservationRepository.save(Reservation.create(
                "reservation-user-service-1",
                "slot-user-service-1",
                "user-service-7",
                2,
                ReservationStatus.CONFIRMED,
                "user-service-7"
        ));

        userService.deleteUser("user-service-7", "system");

        Reservation reservation = reservationRepository.findById("reservation-user-service-1").orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals("system", reservation.getUpdatedBy());
    }
}
