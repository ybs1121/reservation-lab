package com.toy.reservationlab.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "reservation-lab.distributed-lock.enabled=false")
class ReservationConcurrencyWithoutLockTest extends ReservationConcurrencyTestSupport {

    @Test
    void sleep을_두고_순차적으로_예약하면_수용_인원을_초과하지_않는다() throws InterruptedException {
        TestData data = initTestData("sleep-no-lock", 2);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        Queue<String> failCodes = new ConcurrentLinkedQueue<>();

        reserve(data, 0, successCount, failCount, failCodes);
        Thread.sleep(100);
        reserve(data, 1, successCount, failCount, failCodes);

        long activePartySize = getActivePartySize(data.slotId());
        ReservationSlot slot = reservationSlotRepository.findById(data.slotId()).orElseThrow();

        assertEquals(1, successCount.get());
        assertEquals(1, failCount.get());
        assertEquals(SLOT_CAPACITY, activePartySize);
        assertEquals(SLOT_CAPACITY, slot.getCapacity());
    }

    @Test
    void 분산락_적용_전에는_동시_예약_시_수용_인원_초과가_재현된다() throws InterruptedException {
        ConcurrencyResult overbookedResult = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPT_COUNT; attempt++) {
            ConcurrencyResult result = runConcurrentReservation(
                    "latch-no-lock-" + attempt,
                    "분산락 적용 전 동시 예약 attempt=" + attempt
            );
            if (result.isOverbooked()) {
                overbookedResult = result;
                break;
            }
        }

        assertTrue(
                overbookedResult != null,
                "분산락 적용 전 동시 요청에서는 수용 인원 초과 예약이 재현되어야 한다."
        );
    }
}
