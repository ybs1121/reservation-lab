package com.toy.reservationlab.reservationhold.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ReservationHoldMessageSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String serialize(ReservationHoldRequestedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize reservation hold message.", exception);
        }
    }

    public ReservationHoldRequestedMessage deserialize(String message) {
        try {
            return objectMapper.readValue(message, ReservationHoldRequestedMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize reservation hold message.", exception);
        }
    }
}
