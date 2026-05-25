package com.toy.reservationlab.reservationhold.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import com.toy.reservationlab.user.service.UserService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "reservation-lab.distributed-lock.enabled=true",
        "reservation-lab.reservation-hold.enabled=true",
        "reservation-lab.reservation-hold.ttl-seconds=60",
        "reservation-lab.reservation-hold.user-active-hold-max-count=3"
})
@AutoConfigureMockMvc
class ReservationHoldControllerTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Test
    void 임시_점유를_생성하고_조회하고_해제한다() throws Exception {
        createUser("hold-controller-user-1", "010-5200-0001");
        createSlot("hold-controller-restaurant-1", "hold-controller-slot-1", 1);

        String response = mockMvc.perform(post("/reservation-holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "hold-controller-slot-1",
                                  "userId": "hold-controller-user-1",
                                  "partySize": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slotId").value("hold-controller-slot-1"))
                .andExpect(jsonPath("$.data.ttlSeconds").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String holdId = response.replaceAll(".*\\\"holdId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/reservation-holds/{holdId}", holdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdId").value(holdId));

        mockMvc.perform(delete("/reservation-holds/{holdId}", holdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "hold-controller-user-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 임시_점유를_확정하면_예약_응답을_반환한다() throws Exception {
        createUser("hold-controller-user-2", "010-5200-0002");
        createSlot("hold-controller-restaurant-2", "hold-controller-slot-2", 1);

        String response = mockMvc.perform(post("/reservation-holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "hold-controller-slot-2",
                                  "userId": "hold-controller-user-2",
                                  "partySize": 1
                                }
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String holdId = response.replaceAll(".*\\\"holdId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/reservation-holds/{holdId}/confirm", holdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": "hold-controller-reservation-1",
                                  "userId": "hold-controller-user-2",
                                  "createdBy": "hold-controller-user-2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationId").value("hold-controller-reservation-1"))
                .andExpect(jsonPath("$.data.slotId").value("hold-controller-slot-2"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
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
