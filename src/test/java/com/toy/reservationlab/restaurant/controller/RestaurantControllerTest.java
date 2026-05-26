package com.toy.reservationlab.restaurant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.reservation.service.ReservationService;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import com.toy.reservationlab.user.service.UserService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserService userService;

    @Test
    void 식당을_생성하면_식당_응답을_반환한다() throws Exception {
        mockMvc.perform(post("/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restaurantId": "restaurant-controller-1",
                                  "name": "테스트 식당",
                                  "address": "서울시 강남구",
                                  "status": "OPEN",
                                  "createdBy": "user-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.restaurantId").value("restaurant-controller-1"))
                .andExpect(jsonPath("$.data.name").value("테스트 식당"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.delYn").value("N"));
    }

    @Test
    void 식당을_조회하면_식당_응답을_반환한다() throws Exception {
        restaurantService.createRestaurant(
                "restaurant-controller-2",
                "조회 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        mockMvc.perform(get("/restaurants/restaurant-controller-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.restaurantId").value("restaurant-controller-2"))
                .andExpect(jsonPath("$.data.name").value("조회 식당"));
    }

    @Test
    void 존재하지_않는_식당을_조회하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/restaurants/not-found"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("식당을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.code").value("RST00001"));
    }

    @Test
    void 인기_식당을_조회하면_전체기간과_최근_인기_응답을_반환한다() throws Exception {
        restaurantService.createRestaurant(
                "controller-popular-1",
                "인기 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );
        reservationSlotService.createReservationSlot(
                "controller-popular-slot-1",
                "controller-popular-1",
                LocalDate.now().plusDays(1),
                "18:00",
                2,
                ReservationSlotStatus.AVAILABLE,
                "user-1"
        );
        userService.createUser("controller-popular-user-1", "인기 사용자", "010-4100-1001", "user-1");
        reservationService.createReservation(
                "controller-popular-rsv-1",
                "controller-popular-slot-1",
                "controller-popular-user-1",
                1,
                "user-1"
        );

        mockMvc.perform(get("/restaurants/popular")
                        .param("limit", "10")
                        .param("recentDays", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allTime[0].restaurantId").value("controller-popular-1"))
                .andExpect(jsonPath("$.data.allTime[0].reservationCount").value(1))
                .andExpect(jsonPath("$.data.recent[0].restaurantId").value("controller-popular-1"))
                .andExpect(jsonPath("$.data.recent[0].reservationCount").value(1));
    }

    @Test
    void 식당을_수정하면_식당_응답을_반환한다() throws Exception {
        restaurantService.createRestaurant(
                "restaurant-controller-3",
                "수정 전 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        mockMvc.perform(put("/restaurants/restaurant-controller-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "수정 후 식당",
                                  "address": "서울시 마포구",
                                  "status": "CLOSED",
                                  "updatedBy": "user-2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.restaurantId").value("restaurant-controller-3"))
                .andExpect(jsonPath("$.data.name").value("수정 후 식당"))
                .andExpect(jsonPath("$.data.address").value("서울시 마포구"))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void 식당을_삭제하면_삭제된_식당_응답을_반환한다() throws Exception {
        restaurantService.createRestaurant(
                "restaurant-controller-4",
                "삭제 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "user-1"
        );

        mockMvc.perform(delete("/restaurants/restaurant-controller-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "updatedBy": "user-2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.restaurantId").value("restaurant-controller-4"))
                .andExpect(jsonPath("$.data.delYn").value("Y"));
    }
}
