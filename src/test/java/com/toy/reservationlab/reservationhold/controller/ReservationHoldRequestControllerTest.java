package com.toy.reservationlab.reservationhold.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.reservationlab.common.outbox.OutboxEvent;
import com.toy.reservationlab.common.outbox.OutboxEventRepository;
import com.toy.reservationlab.common.outbox.OutboxEventStatus;
import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequest;
import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequestStatus;
import com.toy.reservationlab.reservationhold.repository.ReservationHoldRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "reservation-lab.reservation-hold-request.enabled=true",
        "spring.rabbitmq.dynamic=false"
})
@AutoConfigureMockMvc
class ReservationHoldRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ReservationHoldRequestRepository reservationHoldRequestRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void 비동기_hold_요청을_접수하면_Request와_Outbox를_같이_저장한다() throws Exception {
        String response = mockMvc.perform(post("/reservation-hold-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": "hold-request-controller-slot-1",
                                  "userId": "hold-request-controller-user-1",
                                  "partySize": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").isString())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.holdId").doesNotExist())
                .andExpect(jsonPath("$.data.failureCode").doesNotExist())
                .andExpect(jsonPath("$.data.failureMessage").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).get("data");
        ReservationHoldRequest savedRequest = reservationHoldRequestRepository.findById(data.get("requestId").asText())
                .orElseThrow();

        assertThat(savedRequest.getSlotId()).isEqualTo("hold-request-controller-slot-1");
        assertThat(savedRequest.getUserId()).isEqualTo("hold-request-controller-user-1");
        assertThat(savedRequest.getPartySize()).isEqualTo(2);
        assertThat(savedRequest.getStatus()).isEqualTo(ReservationHoldRequestStatus.PENDING);
        OutboxEvent outboxEvent = outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(savedRequest.getRequestId()))
                .findFirst()
                .orElseThrow();
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.READY);
        assertThat(outboxEvent.getEventType()).isEqualTo("RESERVATION_HOLD_REQUESTED");
        assertThat(outboxEvent.getPayload()).contains(savedRequest.getRequestId());
        assertThat(outboxEvent.getPayload()).contains("hold-request-controller-slot-1");

        mockMvc.perform(get("/reservation-hold-requests/{requestId}", savedRequest.getRequestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value(savedRequest.getRequestId()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

}
