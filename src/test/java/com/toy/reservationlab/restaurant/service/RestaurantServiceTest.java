package com.toy.reservationlab.restaurant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.restaurant.entity.Restaurant;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.repository.RestaurantRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RestaurantServiceTest {

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private ReservationSlotRepository reservationSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void 식당을_생성하면_저장된다() {
        Restaurant restaurant = restaurantService.createRestaurant(
                "restaurant-service-1",
                "테스트 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        assertEquals("restaurant-service-1", restaurant.getRestaurantId());
        assertTrue(restaurantRepository.existsById("restaurant-service-1"));
    }

    @Test
    void 식당을_조회하면_저장된_식당을_반환한다() {
        restaurantService.createRestaurant(
                "restaurant-service-2",
                "테스트 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        Restaurant restaurant = restaurantService.getRestaurant("restaurant-service-2");

        assertEquals("테스트 식당", restaurant.getName());
    }

    @Test
    void 존재하지_않는_식당을_조회하면_비즈니스_예외가_발생한다() {
        BizException exception = assertThrows(
                BizException.class,
                () -> restaurantService.getRestaurant("not-found")
        );

        assertEquals("RST00001", exception.getCode());
    }

    @Test
    void 예약슬롯_생성_가능_여부는_식당_규칙을_따른다() {
        restaurantService.createRestaurant(
                "restaurant-service-3",
                "운영 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        restaurantService.createRestaurant(
                "restaurant-service-4",
                "닫힌 식당",
                "서울시 강남구",
                RestaurantStatus.CLOSED,
                "user-1"
        );

        assertTrue(restaurantService.canCreateReservationSlot("restaurant-service-3"));
        assertFalse(restaurantService.canCreateReservationSlot("restaurant-service-4"));
    }

    @Test
    void 식당을_수정하면_변경된_값이_반영된다() {
        restaurantService.createRestaurant(
                "restaurant-service-5",
                "수정 전 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        Restaurant restaurant = restaurantService.updateRestaurant(
                "restaurant-service-5",
                "수정 후 식당",
                "서울시 마포구",
                RestaurantStatus.CLOSED,
                "user-2"
        );

        assertEquals("수정 후 식당", restaurant.getName());
        assertEquals("서울시 마포구", restaurant.getAddress());
        assertEquals(RestaurantStatus.CLOSED, restaurant.getStatus());
        assertEquals("user-2", restaurant.getUpdatedBy());
    }

    @Test
    void 미래_확정_예약이_없으면_식당을_삭제할_수_있다() {
        restaurantService.createRestaurant(
                "restaurant-service-6",
                "삭제 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        Restaurant restaurant = restaurantService.deleteRestaurant("restaurant-service-6", "user-2");

        assertTrue(restaurant.isDeleted());
        assertEquals("user-2", restaurant.getUpdatedBy());
    }

    @Test
    void 미래_확정_예약이_있으면_식당을_삭제할_수_없다() {
        restaurantService.createRestaurant(
                "restaurant-service-7",
                "예약 존재 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        reservationSlotRepository.save(ReservationSlot.create(
                "slot-service-1",
                "restaurant-service-7",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        ));
        reservationRepository.save(Reservation.create(
                "reservation-service-1",
                "slot-service-1",
                "user-1",
                2,
                ReservationStatus.CONFIRMED,
                "user-1"
        ));

        BizException exception = assertThrows(
                BizException.class,
                () -> restaurantService.deleteRestaurant("restaurant-service-7", "user-2")
        );

        assertEquals("RST00002", exception.getCode());
    }
}
