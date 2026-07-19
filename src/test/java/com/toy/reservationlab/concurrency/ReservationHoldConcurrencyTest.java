package com.toy.reservationlab.concurrency;

import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_HOLD_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.common.component.DistributedLockAspect;
import com.toy.reservationlab.common.component.DistributedLockKeyParser;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservation.service.ReservationService;
import com.toy.reservationlab.reservationhold.component.ReservationHoldData;
import com.toy.reservationlab.reservationhold.component.ReservationHoldStore;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldConfirmRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldCreateRequest;
import com.toy.reservationlab.reservationhold.service.ReservationHoldConfirmService;
import com.toy.reservationlab.reservationhold.service.ReservationHoldReleaseService;
import com.toy.reservationlab.reservationhold.service.ReservationHoldService;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import com.toy.reservationlab.user.service.UserService;
import java.time.LocalDate;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import org.redisson.api.RedissonClient;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "reservation-lab.distributed-lock.enabled=true",
        "reservation-lab.reservation-hold.enabled=true",
        "reservation-lab.reservation-hold-request.enabled=false",
        "reservation-lab.reservation-hold.ttl-seconds=60",
        "reservation-lab.reservation-hold.user-active-hold-max-count=3"
})
class ReservationHoldConcurrencyTest {

    private static final int CONCURRENT_REQUEST_COUNT = 4;
    private static final String USER_ID = "hold-concurrency-user";
    private static final String RESTAURANT_ID = "hold-concurrency-restaurant";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ReservationHoldService reservationHoldService;

    @Autowired
    private ReservationHoldStore reservationHoldStore;

    @Autowired
    private UserService userService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Autowired
    private RedissonClient redissonClient;

    @Test
    void 같은_사용자가_서로_다른_슬롯에_동시에_hold를_요청해도_최대_3개만_생성된다() throws Exception {
        createTestData();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUEST_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUEST_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUEST_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        Queue<String> failureCodes = new ConcurrentLinkedQueue<>();

        try {
            for (int index = 0; index < CONCURRENT_REQUEST_COUNT; index++) {
                String slotId = slotId(index);
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        reservationHoldService.createHold(new ReservationHoldCreateRequest(slotId, USER_ID, 1));
                        successCount.incrementAndGet();
                    } catch (BizException exception) {
                        failureCodes.add(exception.getCode());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        failureCodes.add("INTERRUPTED");
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(3, successCount.get());
        assertEquals(3, reservationHoldStore.countByUserId(USER_ID));
        assertEquals(1, failureCodes.size());
        assertTrue(failureCodes.contains(RESERVATION_HOLD_LIMIT_EXCEEDED.getCode()));
    }

    @Test
    void 같은_hold의_확정과_해제를_동시에_요청하면_하나만_성공해야_한다() throws Exception {
        String suffix = UUID.randomUUID().toString();
        ReservationHoldData hold = new ReservationHoldData(
                "hold-confirm-release-" + suffix,
                "slot-confirm-release-" + suffix,
                "user-confirm-release-" + suffix,
                1
        );
        reservationHoldStore.save(hold, 60);

        CountDownLatch confirmReachedReservationCreate = new CountDownLatch(1);
        CountDownLatch continueConfirm = new CountDownLatch(1);
        ReservationService reservationService = blockingReservationService(
                hold,
                confirmReachedReservationCreate,
                continueConfirm
        );
        ReservationHoldService holdService = holdServiceWithLockedConfirmAndRelease(reservationService);
        AtomicInteger successCount = new AtomicInteger();
        Queue<String> failureCodes = new ConcurrentLinkedQueue<>();
        CountDownLatch releaseStarted = new CountDownLatch(1);
        CountDownLatch releaseFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> confirmFuture = executor.submit(() -> {
                holdService.confirmHold(
                        hold.holdId(),
                        new ReservationHoldConfirmRequest(
                                "reservation-confirm-release-" + suffix,
                                hold.userId(),
                                hold.userId()
                        )
                );
                successCount.incrementAndGet();
            });

            assertTrue(confirmReachedReservationCreate.await(5, TimeUnit.SECONDS));
            Future<?> releaseFuture = executor.submit(() -> {
                releaseStarted.countDown();
                try {
                    holdService.releaseHold(hold.holdId(), hold.userId());
                    successCount.incrementAndGet();
                } catch (BizException exception) {
                    failureCodes.add(exception.getCode());
                } finally {
                    releaseFinished.countDown();
                }
            });

            assertTrue(releaseStarted.await(5, TimeUnit.SECONDS));
            releaseFinished.await(1, TimeUnit.SECONDS);
            continueConfirm.countDown();
            confirmFuture.get(5, TimeUnit.SECONDS);
            releaseFuture.get(5, TimeUnit.SECONDS);
        } finally {
            continueConfirm.countDown();
            executor.shutdownNow();
        }

        assertEquals(
                1,
                successCount.get(),
                "같은 hold의 확정과 해제 중 하나만 성공해야 한다."
        );
        assertEquals(1, failureCodes.size());
        assertTrue(failureCodes.contains("RSH00001"));
    }

    private ReservationService blockingReservationService(
            ReservationHoldData hold,
            CountDownLatch confirmReachedReservationCreate,
            CountDownLatch continueConfirm
    ) {
        ReservationService reservationService = mock(ReservationService.class);
        Reservation reservation = Reservation.create(
                "reservation-confirm-release-result",
                hold.slotId(),
                hold.userId(),
                hold.partySize(),
                ReservationStatus.CONFIRMED,
                hold.userId()
        );
        when(reservationService.createReservation(
                anyString(),
                eq(hold.slotId()),
                eq(hold.userId()),
                eq(hold.partySize()),
                eq(hold.userId())
        )).thenAnswer(invocation -> {
            confirmReachedReservationCreate.countDown();
            if (!continueConfirm.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("해제 요청이 제한 시간 안에 실행되지 않았다.");
            }
            return reservation;
        });
        return reservationService;
    }

    private ReservationHoldService holdServiceWithLockedConfirmAndRelease(ReservationService reservationService) {
        ReservationHoldConfirmService confirmTarget = new ReservationHoldConfirmService(
                reservationHoldStore,
                reservationService
        );
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(confirmTarget);
        proxyFactory.addAspect(new DistributedLockAspect(
                redissonClient,
                new DistributedLockKeyParser()
        ));
        ReservationHoldConfirmService confirmService = proxyFactory.getProxy();

        ReservationHoldReleaseService releaseTarget = new ReservationHoldReleaseService(reservationHoldStore);
        AspectJProxyFactory releaseProxyFactory = new AspectJProxyFactory(releaseTarget);
        releaseProxyFactory.addAspect(new DistributedLockAspect(
                redissonClient,
                new DistributedLockKeyParser()
        ));
        ReservationHoldReleaseService releaseService = releaseProxyFactory.getProxy();

        return new ReservationHoldService(
                reservationHoldStore,
                confirmService,
                releaseService,
                mock(ReservationRepository.class),
                mock(ReservationSlotRepository.class),
                mock(com.toy.reservationlab.user.repository.UserRepository.class)
        );
    }

    private void createTestData() {
        userService.createUser(USER_ID, "hold concurrency user", "010-7190-0001", "system");
        restaurantService.createRestaurant(
                RESTAURANT_ID,
                "hold concurrency restaurant",
                "Seoul",
                RestaurantStatus.OPEN,
                "system"
        );
        for (int index = 0; index < CONCURRENT_REQUEST_COUNT; index++) {
            reservationSlotService.createReservationSlot(
                    slotId(index),
                    RESTAURANT_ID,
                    LocalDate.now().plusDays(1),
                    "18:0" + index,
                    1,
                    ReservationSlotStatus.AVAILABLE,
                    "system"
            );
        }
    }

    private String slotId(int index) {
        return "hold-concurrency-slot-" + index;
    }
}
