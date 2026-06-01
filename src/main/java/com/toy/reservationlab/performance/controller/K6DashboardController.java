package com.toy.reservationlab.performance.controller;

import com.toy.reservationlab.common.component.ApiResponse;
import com.toy.reservationlab.performance.dto.K6RunCreateRequest;
import com.toy.reservationlab.performance.dto.K6RunResponse;
import com.toy.reservationlab.performance.service.K6DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/performance/k6")
@RequiredArgsConstructor
@Profile("local")
@ConditionalOnProperty(name = "reservation-lab.k6-dashboard.enabled", havingValue = "true")
public class K6DashboardController {

    private final K6DashboardService k6DashboardService;

    @PostMapping("/runs")
    public ApiResponse<K6RunResponse> start(@RequestBody K6RunCreateRequest request) {
        return ApiResponse.success(k6DashboardService.start(request));
    }

    @GetMapping("/runs/latest")
    public ApiResponse<K6RunResponse> getLatest() {
        return ApiResponse.success(k6DashboardService.getLatest());
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<K6RunResponse> get(@PathVariable String id) {
        return ApiResponse.success(k6DashboardService.get(id));
    }
}
