package com.toy.reservationlab.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIf("redisAvailable")
@SpringBootTest(properties = "reservation-lab.distributed-lock.enabled=true")
class ReservationConcurrencyWithDistributedLockTest extends ReservationConcurrencyTestSupport {

    @Test
    void 분산락_적용_후에는_동시_예약해도_수용_인원을_초과하지_않는다() throws InterruptedException {
        ConcurrencyResult result = runConcurrentReservation(
                "latch-lock",
                "분산락 적용 후 동시 예약"
        );

        assertEquals(SLOT_CAPACITY, result.successCount());
        assertEquals(SLOT_CAPACITY, result.activePartySize());
        assertTrue(result.isNotOverbooked());
    }
}
