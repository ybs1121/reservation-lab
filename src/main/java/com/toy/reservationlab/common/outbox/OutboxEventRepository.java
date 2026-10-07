package com.toy.reservationlab.common.outbox;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboxEventStatus status,
            LocalDateTime nextAttemptAt,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEvent event
               SET event.status = :sending,
                   event.claimId = :claimId,
                   event.claimedAt = :claimedAt,
                   event.updatedAt = :claimedAt
             WHERE event.eventId = :eventId
               AND event.status = :ready
               AND event.nextAttemptAt <= :claimedAt
            """)
    int claim(
            @Param("eventId") String eventId,
            @Param("claimId") String claimId,
            @Param("claimedAt") LocalDateTime claimedAt,
            @Param("ready") OutboxEventStatus ready,
            @Param("sending") OutboxEventStatus sending
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEvent event
               SET event.status = :published,
                   event.publishedAt = :publishedAt,
                   event.claimId = null,
                   event.claimedAt = null,
                   event.lastError = null,
                   event.updatedAt = :publishedAt
             WHERE event.eventId = :eventId
               AND event.status = :sending
               AND event.claimId = :claimId
            """)
    int markPublished(
            @Param("eventId") String eventId,
            @Param("claimId") String claimId,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("sending") OutboxEventStatus sending,
            @Param("published") OutboxEventStatus published
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEvent event
               SET event.status = :status,
                   event.retryCount = :retryCount,
                   event.nextAttemptAt = :nextAttemptAt,
                   event.claimId = null,
                   event.claimedAt = null,
                   event.lastError = :lastError,
                   event.updatedAt = :failedAt
             WHERE event.eventId = :eventId
               AND event.status = :sending
               AND event.claimId = :claimId
            """)
    int markPublishFailed(
            @Param("eventId") String eventId,
            @Param("claimId") String claimId,
            @Param("status") OutboxEventStatus status,
            @Param("retryCount") int retryCount,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("failedAt") LocalDateTime failedAt,
            @Param("sending") OutboxEventStatus sending
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OutboxEvent event
               SET event.status = :ready,
                   event.claimId = null,
                   event.claimedAt = null,
                   event.updatedAt = :recoveredAt
             WHERE event.status = :sending
               AND event.claimedAt < :staleBefore
            """)
    int recoverStaleSending(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("recoveredAt") LocalDateTime recoveredAt,
            @Param("sending") OutboxEventStatus sending,
            @Param("ready") OutboxEventStatus ready
    );
}
