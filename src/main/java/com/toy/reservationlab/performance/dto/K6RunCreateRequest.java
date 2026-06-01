package com.toy.reservationlab.performance.dto;

public record K6RunCreateRequest(
        String scenario,
        String baseUrl,
        Integer vus,
        String duration,
        Integer slotCapacity,
        Integer partySize,
        Integer pollIntervalMs,
        Integer maxPolls
) {
}
