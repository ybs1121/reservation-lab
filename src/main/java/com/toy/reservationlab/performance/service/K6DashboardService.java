package com.toy.reservationlab.performance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toy.reservationlab.performance.dto.K6RunCreateRequest;
import com.toy.reservationlab.performance.dto.K6RunResponse;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class K6DashboardService {

    private static final int DEFAULT_VUS = 5;
    private static final int DEFAULT_SLOT_CAPACITY = 10000;
    private static final int DEFAULT_PARTY_SIZE = 1;
    private static final int DEFAULT_POLL_INTERVAL_MS = 500;
    private static final int DEFAULT_MAX_POLLS = 60;
    private static final String DEFAULT_DURATION = "10s";
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ConcurrentMap<String, K6RunState> runs = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Value("${reservation-lab.k6-dashboard.k6-path:}")
    private String configuredK6Path;

    // Dashboard runs are intentionally serialized so one local test does not distort another test's metrics.
    public K6RunResponse start(K6RunCreateRequest request) {
        Optional<K6RunState> running = runs.values().stream()
                .filter(run -> run.status == K6RunStatus.RUNNING)
                .findFirst();
        if (running.isPresent()) {
            return toResponse(running.get());
        }

        String id = String.valueOf(sequence.incrementAndGet());
        K6Scenario scenario = K6Scenario.from(request.scenario());
        K6RunOptions options = K6RunOptions.from(request);
        K6RunState run = K6RunState.create(id, scenario, options);
        runs.put(id, run);

        executorService.submit(() -> execute(run));
        return toResponse(run);
    }

    public K6RunResponse get(String id) {
        K6RunState run = runs.get(id);
        if (run == null) {
            return null;
        }
        return toResponse(run);
    }

    public K6RunResponse getLatest() {
        return runs.values().stream()
                .max((left, right) -> left.startedAt.compareTo(right.startedAt))
                .map(this::toResponse)
                .orElse(null);
    }

    private void execute(K6RunState run) {
        run.startedAt = LocalDateTime.now();
        run.status = K6RunStatus.RUNNING;

        try {
            Path summaryPath = createSummaryPath(run.id);
            run.command = createCommand(run, summaryPath);

            Process process = new ProcessBuilder(run.command)
                    .directory(Path.of("").toAbsolutePath().toFile())
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            run.output = output;
            run.exitCode = exitCode;
            run.summary = readSummary(summaryPath);
            run.status = exitCode == 0 ? K6RunStatus.SUCCEEDED : K6RunStatus.FAILED;
        } catch (IOException e) {
            run.status = K6RunStatus.FAILED;
            run.errorMessage = "k6 실행 파일 또는 스크립트를 찾을 수 없습니다. k6 설치와 경로를 확인해 주세요.";
            run.output = e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            run.status = K6RunStatus.FAILED;
            run.errorMessage = "k6 실행이 중단되었습니다.";
        } finally {
            run.completedAt = LocalDateTime.now();
        }
    }

    private List<String> createCommand(K6RunState run, Path summaryPath) {
        List<String> command = new ArrayList<>();
        command.add(resolveK6Executable());
        command.add("run");
        command.add("--summary-export");
        command.add(summaryPath.toString());
        command.add("-e");
        command.add("BASE_URL=" + run.options.baseUrl());
        command.add("-e");
        command.add("VUS=" + run.options.vus());
        command.add("-e");
        command.add("DURATION=" + run.options.duration());
        command.add("-e");
        command.add("SLOT_CAPACITY=" + run.options.slotCapacity());
        command.add("-e");
        command.add("PARTY_SIZE=" + run.options.partySize());
        command.add("-e");
        command.add("POLL_INTERVAL_MS=" + run.options.pollIntervalMs());
        command.add("-e");
        command.add("MAX_POLLS=" + run.options.maxPolls());
        command.add("-e");
        command.add("RUN_ID=dashboard-" + run.id + "-" + System.currentTimeMillis());
        command.add(run.scenario.scriptPath());
        return command;
    }

    private String resolveK6Executable() {
        if (configuredK6Path != null && !configuredK6Path.isBlank()) {
            return configuredK6Path;
        }

        Path defaultWindowsPath = Path.of("C:\\Program Files\\k6\\k6.exe");
        if (Files.exists(defaultWindowsPath)) {
            return defaultWindowsPath.toString();
        }

        return "k6";
    }

    private Path createSummaryPath(String id) throws IOException {
        Path directory = Path.of("build", "k6-dashboard");
        Files.createDirectories(directory);
        return directory.resolve(id + "-summary.json");
    }

    private Map<String, Object> readSummary(Path summaryPath) throws IOException {
        if (!Files.exists(summaryPath)) {
            return Map.of();
        }
        return objectMapper.readValue(summaryPath.toFile(), new TypeReference<>() {
        });
    }

    private K6RunResponse toResponse(K6RunState run) {
        return new K6RunResponse(
                run.id,
                run.scenario.name(),
                run.status.name(),
                run.startedAt,
                run.completedAt,
                run.exitCode,
                run.command,
                run.output,
                run.errorMessage,
                run.summary
        );
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdownNow();
    }

    private enum K6RunStatus {
        READY,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum K6Scenario {
        SYNC("scripts/k6/reservation-hold-sync.js"),
        ASYNC("scripts/k6/reservation-hold-async.js");

        private final String scriptPath;

        K6Scenario(String scriptPath) {
            this.scriptPath = scriptPath;
        }

        private String scriptPath() {
            return scriptPath;
        }

        private static K6Scenario from(String value) {
            if ("async".equalsIgnoreCase(value)) {
                return ASYNC;
            }
            return SYNC;
        }
    }

    private record K6RunOptions(
            String baseUrl,
            int vus,
            String duration,
            int slotCapacity,
            int partySize,
            int pollIntervalMs,
            int maxPolls
    ) {

        private static K6RunOptions from(K6RunCreateRequest request) {
            return new K6RunOptions(
                    defaultString(request.baseUrl(), DEFAULT_BASE_URL),
                    clamp(request.vus(), DEFAULT_VUS, 1, 200),
                    normalizeDuration(request.duration()),
                    clamp(request.slotCapacity(), DEFAULT_SLOT_CAPACITY, 1, 1_000_000),
                    clamp(request.partySize(), DEFAULT_PARTY_SIZE, 1, 100),
                    clamp(request.pollIntervalMs(), DEFAULT_POLL_INTERVAL_MS, 100, 10_000),
                    clamp(request.maxPolls(), DEFAULT_MAX_POLLS, 1, 300)
            );
        }

        private static String defaultString(String value, String defaultValue) {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return value;
        }

        private static int clamp(Integer value, int defaultValue, int min, int max) {
            int number = value == null ? defaultValue : value;
            if (number < min) {
                return min;
            }
            return Math.min(number, max);
        }

        private static String normalizeDuration(String value) {
            if (value == null || !value.matches("\\d+[sm]")) {
                return DEFAULT_DURATION;
            }
            return value;
        }
    }

    private static class K6RunState {

        private final String id;
        private final K6Scenario scenario;
        private final K6RunOptions options;
        private volatile K6RunStatus status;
        private volatile LocalDateTime startedAt;
        private volatile LocalDateTime completedAt;
        private volatile Integer exitCode;
        private volatile List<String> command;
        private volatile String output;
        private volatile String errorMessage;
        private volatile Map<String, Object> summary;

        private K6RunState(String id, K6Scenario scenario, K6RunOptions options) {
            this.id = id;
            this.scenario = scenario;
            this.options = options;
            this.status = K6RunStatus.READY;
            this.startedAt = LocalDateTime.now();
            this.command = List.of();
            this.output = "";
            this.summary = Map.of();
        }

        private static K6RunState create(String id, K6Scenario scenario, K6RunOptions options) {
            return new K6RunState(id, scenario, options);
        }
    }
}
