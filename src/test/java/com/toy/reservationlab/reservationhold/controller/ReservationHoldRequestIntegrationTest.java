package com.toy.reservationlab.reservationhold.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.reservationlab.common.component.ErrorCode;
import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequestStatus;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;

@Testcontainers
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservation_hold_request_integration;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "reservation-lab.distributed-lock.enabled=true",
        "reservation-lab.reservation-hold.enabled=true",
        "reservation-lab.reservation-hold-request.enabled=true",
        "reservation-lab.reservation-hold.ttl-seconds=60",
        "reservation-lab.reservation-hold.user-active-hold-max-count=3"
})
@AutoConfigureMockMvc
class ReservationHoldRequestIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4")
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> rabbitMq = new GenericContainer<>("rabbitmq:3.13-management")
            .withEnv("RABBITMQ_DEFAULT_USER", "reservation")
            .withEnv("RABBITMQ_DEFAULT_PASS", "reservation")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1));

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbitMq::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitMq.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "reservation");
        registry.add("spring.rabbitmq.password", () -> "reservation");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 비동기_hold_요청은_RabbitMQ_listener가_처리한_뒤_SUCCEEDED로_조회된다() throws Exception {
        createUser("hold-request-integration-user-1", "010-5300-0001");
        createSlot("hold-request-integration-restaurant-1", "hold-request-integration-slot-1", 2);

        String requestId = createHoldRequest(
                "hold-request-integration-slot-1",
                "hold-request-integration-user-1",
                2
        );

        JsonNode data = waitUntilStatus(requestId, ReservationHoldRequestStatus.SUCCEEDED);

        assertThat(data.get("holdId").asText()).isNotBlank();
        assertThat(data.get("failureCode").isNull()).isTrue();
        assertThat(data.get("failureMessage").isNull()).isTrue();
    }

    @Test
    void 비동기_hold_요청_처리에서_비즈니스_실패가_나면_FAILED로_조회된다() throws Exception {
        createUser("hold-request-integration-user-2", "010-5300-0002");
        createSlot("hold-request-integration-restaurant-2", "hold-request-integration-slot-2", 1);

        String requestId = createHoldRequest(
                "hold-request-integration-slot-2",
                "hold-request-integration-user-2",
                2
        );

        JsonNode data = waitUntilStatus(requestId, ReservationHoldRequestStatus.FAILED);

        assertThat(data.get("holdId").isNull()).isTrue();
        assertThat(data.get("failureCode").asText()).isEqualTo(ErrorCode.CAPACITY_EXCEEDED.getCode());
        assertThat(data.get("failureMessage").asText()).isNotBlank();
    }

    private String createHoldRequest(String slotId, String userId, int partySize) throws Exception {
        String response = mockMvc.perform(post("/reservation-hold-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "%s",
                                  "userId": "%s",
                                  "partySize": %d
                                }
                                """.formatted(slotId, userId, partySize)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").isString())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("data").get("requestId").asText();
    }

    private JsonNode waitUntilStatus(String requestId, ReservationHoldRequestStatus expectedStatus) throws Exception {
        JsonNode lastData = null;
        for (int i = 0; i < 30; i++) {
            String response = mockMvc.perform(get("/reservation-hold-requests/{requestId}", requestId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            lastData = objectMapper.readTree(response).get("data");
            if (expectedStatus.name().equals(lastData.get("status").asText())) {
                return lastData;
            }
            Thread.sleep(200);
        }

        throw new AssertionError("expected status " + expectedStatus + " but last data was " + lastData);
    }

    private void createUser(String userId, String phone) {
        userService.createUser(userId, "hold request user", phone, "system");
    }

    private void createSlot(String restaurantId, String slotId, int capacity) {
        restaurantService.createRestaurant(
                restaurantId,
                "hold request restaurant",
                "Seoul",
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
