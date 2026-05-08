package com.toy.reservationlab.restaurant.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RestaurantTest {

    @Test
    void 운영중인_식당은_예약슬롯을_생성할_수_있다() {
        Restaurant restaurant = Restaurant.create(
                "restaurant-1",
                "테스트 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        assertTrue(restaurant.canCreateReservationSlot());
    }

    @Test
    void 닫히거나_중지된_식당은_예약슬롯을_생성할_수_없다() {
        Restaurant closedRestaurant = Restaurant.create(
                "restaurant-1",
                "테스트 식당",
                "서울시 강남구",
                RestaurantStatus.CLOSED,
                "user-1"
        );
        Restaurant suspendedRestaurant = Restaurant.create(
                "restaurant-2",
                "테스트 식당",
                "서울시 강남구",
                RestaurantStatus.SUSPENDED,
                "user-1"
        );

        assertFalse(closedRestaurant.canCreateReservationSlot());
        assertFalse(suspendedRestaurant.canCreateReservationSlot());
    }

    @Test
    void 삭제된_식당은_예약슬롯을_생성할_수_없다() {
        Restaurant restaurant = Restaurant.create(
                "restaurant-1",
                "테스트 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        restaurant.markDeleted("user-2");

        assertTrue(restaurant.isDeleted());
        assertFalse(restaurant.canCreateReservationSlot());
        assertEquals("user-2", restaurant.getUpdatedBy());
    }

    @Test
    void 식당_상태는_정의된_값만_사용한다() {
        assertEquals(3, RestaurantStatus.values().length);
        assertEquals(RestaurantStatus.OPEN, RestaurantStatus.valueOf("OPEN"));
        assertEquals(RestaurantStatus.CLOSED, RestaurantStatus.valueOf("CLOSED"));
        assertEquals(RestaurantStatus.SUSPENDED, RestaurantStatus.valueOf("SUSPENDED"));
    }
}
