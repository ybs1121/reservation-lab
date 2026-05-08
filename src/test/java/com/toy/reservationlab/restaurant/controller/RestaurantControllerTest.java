package com.toy.reservationlab.restaurant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.service.RestaurantService;
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
}
