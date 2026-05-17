package com.toy.reservationlab.concurrency;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservation.service.ReservationService;
import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import com.toy.reservationlab.user.service.UserService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
abstract class ReservationConcurrencyTestSupport {

    protected static final int SLOT_CAPACITY = 1;
    protected static final int CONCURRENT_REQUEST_COUNT = 30;
    protected static final int MAX_ATTEMPT_COUNT = 10;
    private static final List<ReservationStatus> ACTIVE_PARTY_SIZE_STATUSES = List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.NO_SHOW
    );

    @Autowired
    protected ReservationService reservationService;

    @Autowired
    protected ReservationRepository reservationRepository;

    @Autowired
    protected ReservationSlotRepository reservationSlotRepository;

    @Autowired
    protected UserService userService;

    @Autowired
    protected RestaurantService restaurantService;

    @Autowired
    protected ReservationSlotService reservationSlotService;

    protected ConcurrencyResult runConcurrentReservation(String label, String title) throws InterruptedException {
        TestData data = initTestData(label, CONCURRENT_REQUEST_COUNT);
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_REQUEST_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUEST_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUEST_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        Queue<String> failCodes = new ConcurrentLinkedQueue<>();

        for (int index = 0; index < CONCURRENT_REQUEST_COUNT; index++) {
            int requestIndex = index;
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    reserve(data, requestIndex, successCount, failCount, failCodes);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failCount.incrementAndGet();
                    failCodes.add("INTERRUPTED");
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executorService.shutdown();

        long activePartySize = getActivePartySize(data.slotId());
        ReservationSlot slot = reservationSlotRepository.findById(data.slotId()).orElseThrow();
        logResult(
                title,
                data.slotId(),
                successCount.get(),
                failCount.get(),
                failCodes,
                activePartySize,
                slot
        );

        return new ConcurrencyResult(successCount.get(), activePartySize, slot.getCapacity());
    }

    protected void reserve(
            TestData data,
            int index,
            AtomicInteger successCount,
            AtomicInteger failCount,
            Queue<String> failCodes
    ) {
        try {
            reservationService.createReservation(
                    data.reservationIdPrefix() + "-" + index,
                    data.slotId(),
                    data.userIds().get(index),
                    1,
                    data.userIds().get(index)
            );
            successCount.incrementAndGet();
        } catch (BizException exception) {
            failCount.incrementAndGet();
            failCodes.add(exception.getCode());
        }
    }

    protected TestData initTestData(String label, int userCount) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String restaurantId = "cr-" + label + "-" + suffix;
        String slotId = "cs-" + label + "-" + suffix;
        String reservationIdPrefix = "cv-" + label + "-" + suffix;
        Queue<String> userIds = new ConcurrentLinkedQueue<>();

        restaurantService.createRestaurant(
                restaurantId,
                "동시성 테스트 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "system"
        );
        reservationSlotService.createReservationSlot(
                slotId,
                restaurantId,
                LocalDate.now().plusDays(1),
                "18:00",
                SLOT_CAPACITY,
                ReservationSlotStatus.AVAILABLE,
                "system"
        );

        for (int index = 0; index < userCount; index++) {
            String userId = "cu-" + label + "-" + suffix + "-" + index;
            userService.createUser(
                    userId,
                    "동시성 테스트 사용자",
                    "010-" + suffix.substring(0, 4) + "-" + String.format("%04d", index),
                    "system"
            );
            userIds.add(userId);
        }

        return new TestData(slotId, reservationIdPrefix, List.copyOf(userIds));
    }

    protected long getActivePartySize(String slotId) {
        return reservationRepository.sumPartySizeBySlotIdAndStatuses(slotId, ACTIVE_PARTY_SIZE_STATUSES);
    }

    private void logResult(
            String title,
            String slotId,
            int successCount,
            int failCount,
            Queue<String> failCodes,
            long activePartySize,
            ReservationSlot slot
    ) {
        log.info("""

                ==================== {} ====================
                slotId              : {}
                successCount        : {}
                failCount           : {}
                failCodes           : {}
                slotCapacity        : {}
                activePartySize     : {}
                slotStatus          : {}
                overbooked          : {}
                ==================================================
                """,
                title,
                slotId,
                successCount,
                failCount,
                failCodes,
                slot.getCapacity(),
                activePartySize,
                slot.getStatus(),
                activePartySize > slot.getCapacity()
        );
    }

    protected record TestData(
            String slotId,
            String reservationIdPrefix,
            List<String> userIds
    ) {
    }

    protected record ConcurrencyResult(
            int successCount,
            long activePartySize,
            int capacity
    ) {

        boolean isOverbooked() {
            return activePartySize > capacity;
        }

        boolean isNotOverbooked() {
            return activePartySize <= capacity;
        }
    }

    static boolean redisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 6379), 500);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
