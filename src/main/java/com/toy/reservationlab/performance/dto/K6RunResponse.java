package com.toy.reservationlab.performance.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record K6RunResponse(
        String id,
        String scenario,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Integer exitCode,
        List<String> command,
        String output,
        String errorMessage,
        Map<String, Object> summary
) {
}
