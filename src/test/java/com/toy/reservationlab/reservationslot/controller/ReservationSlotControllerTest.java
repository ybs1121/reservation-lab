package com.toy.reservationlab.reservationslot.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationSlotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Test
    void 예약슬롯을_생성하면_예약슬롯_응답을_반환한다() throws Exception {
        restaurantService.createRestaurant(
                "restaurant-slot-controller-1",
                "슬롯 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        mockMvc.perform(post("/reservation-slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "slot-controller-1",
                                  "restaurantId": "restaurant-slot-controller-1",
                                  "slotDate": "%s",
                                  "slotTime": "18:00",
                                  "capacity": 10,
                                  "status": "AVAILABLE",
                                  "createdBy": "user-1"
                                }
                                """.formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slotId").value("slot-controller-1"))
                .andExpect(jsonPath("$.data.restaurantId").value("restaurant-slot-controller-1"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.delYn").value("N"));
    }

    @Test
    void 예약슬롯을_조회하면_예약슬롯_응답을_반환한다() throws Exception {
        restaurantService.createRestaurant(
                "restaurant-slot-controller-2",
                "조회 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        reservationSlotService.createReservationSlot(
                "slot-controller-2",
                "restaurant-slot-controller-2",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );

        mockMvc.perform(get("/reservation-slots/slot-controller-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slotId").value("slot-controller-2"))
                .andExpect(jsonPath("$.data.capacity").value(10));
    }

    @Test
    void 예약슬롯을_수정하면_예약슬롯_응답을_반환한다() throws Exception {
        restaurantService.createRestaurant(
                "restaurant-slot-controller-3",
                "수정 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        reservationSlotService.createReservationSlot(
                "slot-controller-3",
                "restaurant-slot-controller-3",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );

        mockMvc.perform(put("/reservation-slots/slot-controller-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restaurantId": "restaurant-slot-controller-3",
                                  "slotDate": "%s",
                                  "slotTime": "19:00",
                                  "capacity": 12,
                                  "status": "CLOSED",
                                  "updatedBy": "user-2"
                                }
                                """.formatted(LocalDate.now().plusDays(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slotId").value("slot-controller-3"))
                .andExpect(jsonPath("$.data.slotTime").value("19:00"))
                .andExpect(jsonPath("$.data.capacity").value(12))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void 예약슬롯을_삭제하면_삭제된_예약슬롯_응답을_반환한다() throws Exception {
        restaurantService.createRestaurant(
                "restaurant-slot-controller-4",
                "삭제 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        reservationSlotService.createReservationSlot(
                "slot-controller-4",
                "restaurant-slot-controller-4",
                LocalDate.now().plusDays(1),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );

        mockMvc.perform(delete("/reservation-slots/slot-controller-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "updatedBy": "user-2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slotId").value("slot-controller-4"))
                .andExpect(jsonPath("$.data.delYn").value("Y"));
    }
}
