package com.toy.reservationlab.common.outbox;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OutboxEventStateService {

    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventStateService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    public List<OutboxEventSnapshot> findReadyEvents(LocalDateTime now, int batchSize) {
        return outboxEventRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxEventStatus.READY,
                        now,
                        PageRequest.of(0, batchSize)
                ).stream()
                .map(OutboxEventSnapshot::from)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String eventId, String claimId, LocalDateTime claimedAt) {
        return outboxEventRepository.claim(
                eventId,
                claimId,
                claimedAt,
                OutboxEventStatus.READY,
                OutboxEventStatus.SENDING
        ) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(String eventId, String claimId, LocalDateTime publishedAt) {
        outboxEventRepository.markPublished(
                eventId,
                claimId,
                publishedAt,
                OutboxEventStatus.SENDING,
                OutboxEventStatus.PUBLISHED
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublishFailed(
            OutboxEventSnapshot event,
            String claimId,
            LocalDateTime failedAt,
            String errorMessage
    ) {
        int retryCount = event.retryCount() + 1;
        OutboxEventStatus status = retryCount >= MAX_RETRY_COUNT
                ? OutboxEventStatus.DEAD
                : OutboxEventStatus.READY;
        LocalDateTime nextAttemptAt = status == OutboxEventStatus.DEAD
                ? failedAt
                : failedAt.plusSeconds(backoffSeconds(retryCount));
        outboxEventRepository.markPublishFailed(
                event.eventId(),
                claimId,
                status,
                retryCount,
                nextAttemptAt,
                truncate(errorMessage),
                failedAt,
                OutboxEventStatus.SENDING
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleSending(LocalDateTime staleBefore, LocalDateTime recoveredAt) {
        return outboxEventRepository.recoverStaleSending(
                staleBefore,
                recoveredAt,
                OutboxEventStatus.SENDING,
                OutboxEventStatus.READY
        );
    }

    private long backoffSeconds(int retryCount) {
        return switch (retryCount) {
            case 1 -> 1;
            case 2 -> 5;
            case 3 -> 30;
            case 4 -> 60;
            default -> 300;
        };
    }

    private String truncate(String errorMessage) {
        if (errorMessage == null) {
            return "Unknown publish error";
        }
        return errorMessage.length() <= 500 ? errorMessage : errorMessage.substring(0, 500);
    }
}
