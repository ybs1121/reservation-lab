package com.toy.reservationlab.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class ReservationConcurrencyTest {

    private static final int SLOT_CAPACITY = 1;
    private static final int CONCURRENT_REQUEST_COUNT = 30;
    private static final int MAX_ATTEMPT_COUNT = 10;
    private static final List<ReservationStatus> ACTIVE_PARTY_SIZE_STATUSES = List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.NO_SHOW
    );

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSlotRepository reservationSlotRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Test
    void sleep을_두고_순차적으로_예약하면_수용_인원을_초과하지_않는다() throws InterruptedException {
        TestData data = initTestData("sleep", 2);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        Queue<String> failCodes = new ConcurrentLinkedQueue<>();

        reserve(data, 0, successCount, failCount, failCodes);
        Thread.sleep(100);
        reserve(data, 1, successCount, failCount, failCodes);

        long activePartySize = getActivePartySize(data.slotId());
        ReservationSlot slot = reservationSlotRepository.findById(data.slotId()).orElseThrow();
        logResult("sleep 순차 예약", data.slotId(), successCount.get(), failCount.get(), failCodes, activePartySize, slot);

        assertEquals(1, successCount.get());
        assertEquals(1, failCount.get());
        assertEquals(SLOT_CAPACITY, activePartySize);
    }

    @Test
    void CountDownLatch로_동시에_예약하면_0단계_동시성_문제가_발생한다() throws InterruptedException {
        ConcurrencyResult overbookedResult = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPT_COUNT; attempt++) {
            ConcurrencyResult result = runConcurrentReservation(attempt);
            if (result.isOverbooked()) {
                overbookedResult = result;
                break;
            }
        }

        assertTrue(
                overbookedResult != null,
                "동시 요청에서 수용 인원 초과 예약이 재현되어야 한다."
        );
    }

    private ConcurrencyResult runConcurrentReservation(int attempt) throws InterruptedException {
        TestData data = initTestData("latch-" + attempt, CONCURRENT_REQUEST_COUNT);
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
                "CountDownLatch 동시 예약 attempt=" + attempt,
                data.slotId(),
                successCount.get(),
                failCount.get(),
                failCodes,
                activePartySize,
                slot
        );

        return new ConcurrencyResult(activePartySize, slot.getCapacity());
    }

    private void reserve(
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

    private TestData initTestData(String label, int userCount) {
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

    private long getActivePartySize(String slotId) {
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

    private record TestData(
            String slotId,
            String reservationIdPrefix,
            List<String> userIds
    ) {
    }

    private record ConcurrencyResult(
            long activePartySize,
            int capacity
    ) {

        boolean isOverbooked() {
            return activePartySize > capacity;
        }
    }
}
