package com.toy.reservationlab.restaurant.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toy.reservationlab.restaurant.entity.PopularityPeriodType;
import com.toy.reservationlab.restaurant.entity.RestaurantPopularity;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.repository.RestaurantPopularityRepository;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import java.time.LocalDateTime;
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
    private RestaurantPopularityRepository restaurantPopularityRepository;

    @Test
    void createRestaurant_returnsRestaurantResponse() throws Exception {
        mockMvc.perform(post("/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restaurantId": "restaurant-controller-1",
                                  "name": "Controller Restaurant",
                                  "address": "Seoul Gangnam",
                                  "status": "OPEN",
                                  "createdBy": "user-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.restaurantId").value("restaurant-controller-1"))
                .andExpect(jsonPath("$.data.name").value("Controller Restaurant"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.delYn").value("N"));
    }

    @Test
    void getRestaurant_returnsRestaurantResponse() throws Exception {
        restaurantService.createRestaurant(
                "restaurant-controller-2",
                "Find Restaurant",
                "Seoul Gangnam",
                RestaurantStatus.OPEN,
                "user-1"
        );

        mockMvc.perform(get("/restaurants/restaurant-controller-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.restaurantId").value("restaurant-controller-2"))
                .andExpect(jsonPath("$.data.name").value("Find Restaurant"));
    }

    @Test
    void getRestaurant_notFound_returns400() throws Exception {
        mockMvc.perform(get("/restaurants/not-found"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RST00001"));
    }

    @Test
    void getPopularRestaurants_returnsBatchAggregationResponse() throws Exception {
        restaurantPopularityRepository.deleteAll();
        restaurantService.createRestaurant(
                "controller-popular-1",
                "Popular Restaurant",
                "Seoul Gangnam",
                RestaurantStatus.OPEN,
                "user-1"
        );
        savePopularity("controller-popular-all-time", "controller-popular-1", PopularityPeriodType.ALL_TIME);
        savePopularity("controller-popular-last-7", "controller-popular-1", PopularityPeriodType.LAST_7_DAYS);
        savePopularity("controller-popular-last-30", "controller-popular-1", PopularityPeriodType.LAST_30_DAYS);
        savePopularity("controller-popular-last-90", "controller-popular-1", PopularityPeriodType.LAST_90_DAYS);

        mockMvc.perform(get("/restaurants/popular")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allTime[0].restaurantId").value("controller-popular-1"))
                .andExpect(jsonPath("$.data.allTime[0].reservationCount").value(1))
                .andExpect(jsonPath("$.data.last7Days[0].restaurantId").value("controller-popular-1"))
                .andExpect(jsonPath("$.data.last30Days[0].restaurantId").value("controller-popular-1"))
                .andExpect(jsonPath("$.data.last90Days[0].restaurantId").value("controller-popular-1"));
    }

    @Test
    void updateRestaurant_returnsRestaurantResponse() throws Exception {
        restaurantService.createRestaurant(
                "restaurant-controller-3",
                "Before Update",
                "Seoul Gangnam",
                RestaurantStatus.OPEN,
                "user-1"
        );

        mockMvc.perform(put("/restaurants/restaurant-controller-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "After Update",
                                  "address": "Seoul Mapo",
                                  "status": "CLOSED",
                                  "updatedBy": "user-2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.restaurantId").value("restaurant-controller-3"))
                .andExpect(jsonPath("$.data.name").value("After Update"))
                .andExpect(jsonPath("$.data.address").value("Seoul Mapo"))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void deleteRestaurant_returnsDeletedRestaurantResponse() throws Exception {
        restaurantService.createRestaurant(
                "restaurant-controller-4",
                "Delete Restaurant",
                "Seoul Gangnam",
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

    private void savePopularity(String popularityId, String restaurantId, PopularityPeriodType periodType) {
        restaurantPopularityRepository.save(RestaurantPopularity.create(
                popularityId,
                restaurantId,
                periodType,
                1,
                LocalDateTime.now(),
                "system"
        ));
    }
}
