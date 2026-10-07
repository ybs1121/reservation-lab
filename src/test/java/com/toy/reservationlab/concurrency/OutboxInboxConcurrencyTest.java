package com.toy.reservationlab.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.toy.reservationlab.common.inbox.InboxMessageRepository;
import com.toy.reservationlab.common.outbox.OutboxEvent;
import com.toy.reservationlab.common.outbox.OutboxEventRepository;
import com.toy.reservationlab.common.outbox.OutboxEventStateService;
import com.toy.reservationlab.common.outbox.OutboxEventStatus;
import com.toy.reservationlab.reservationhold.component.ReservationHoldInboxHandler;
import com.toy.reservationlab.reservationhold.component.ReservationHoldRequestProcessor;
import com.toy.reservationlab.reservationhold.component.ReservationHoldRequestedMessage;
import com.toy.reservationlab.reservationhold.component.ReservationHoldRequestedPayload;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = {
        "reservation-lab.reservation-hold.enabled=true",
        "reservation-lab.reservation-hold-request.enabled=true",
        "reservation-lab.outbox-publisher.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class OutboxInboxConcurrencyTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventStateService outboxEventStateService;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private ReservationHoldInboxHandler reservationHoldInboxHandler;

    @Autowired
    private ReservationHoldRequestProcessor reservationHoldRequestProcessor;

    @BeforeEach
    void setUp() {
        inboxMessageRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    void 두_Publisher가_같은_Outbox를_동시에_선점해도_하나만_성공한다() throws Exception {
        LocalDateTime occurredAt = LocalDateTime.now();
        OutboxEvent event = OutboxEvent.create(
                "outbox-concurrency-event",
                "RESERVATION_HOLD_REQUEST",
                "outbox-concurrency-request",
                "RESERVATION_HOLD_REQUESTED",
                1,
                "{}",
                occurredAt
        );
        outboxEventRepository.saveAndFlush(event);
        AtomicInteger claimedCount = runConcurrently(index -> {
            if (outboxEventStateService.claim(
                    event.getEventId(),
                    "claim-" + index,
                    LocalDateTime.now()
            )) {
                return 1;
            }
            return 0;
        });

        assertEquals(1, claimedCount.get());
        assertEquals(
                OutboxEventStatus.SENDING,
                outboxEventRepository.findById(event.getEventId()).orElseThrow().getStatus()
        );
    }

    @Test
    void 두_Consumer가_같은_messageId를_동시에_받아도_Processor는_한번만_실행된다() throws Exception {
        ReservationHoldRequestedMessage message = new ReservationHoldRequestedMessage(
                "inbox-concurrency-message",
                "RESERVATION_HOLD_REQUESTED",
                1,
                LocalDateTime.now().toString(),
                new ReservationHoldRequestedPayload(
                        "inbox-concurrency-request",
                        "inbox-concurrency-slot",
                        "inbox-concurrency-user",
                        1
                )
        );

        AtomicInteger completedCount = runConcurrently(index -> {
            reservationHoldInboxHandler.handle(message);
            return 1;
        });

        assertEquals(2, completedCount.get());
        assertEquals(1, inboxMessageRepository.count());
        verify(reservationHoldRequestProcessor, times(1)).process(message.payload().requestId());
    }

    private AtomicInteger runConcurrently(ConcurrentOperation operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger result = new AtomicInteger();
        try {
            for (int index = 0; index < 2; index++) {
                int requestIndex = index;
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        result.addAndGet(operation.execute(requestIndex));
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ConcurrentOperation {
        int execute(int index) throws Exception;
    }

    @TestConfiguration
    static class TestProcessorConfig {

        @Bean
        @Primary
        ReservationHoldRequestProcessor reservationHoldRequestProcessor() {
            return mock(ReservationHoldRequestProcessor.class);
        }
    }
}
