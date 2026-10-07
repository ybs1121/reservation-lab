package com.toy.reservationlab.common.inbox;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO inbox_message (
                message_id,
                request_id,
                event_type,
                status,
                received_at,
                created_at,
                updated_at
            ) VALUES (
                :messageId,
                :requestId,
                :eventType,
                'RECEIVED',
                :receivedAt,
                :receivedAt,
                :receivedAt
            )
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("messageId") String messageId,
            @Param("requestId") String requestId,
            @Param("eventType") String eventType,
            @Param("receivedAt") LocalDateTime receivedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InboxMessage message
               SET message.status = :processed,
                   message.processedAt = :processedAt,
                   message.updatedAt = :processedAt
             WHERE message.messageId = :messageId
               AND message.status = :received
            """)
    int markProcessed(
            @Param("messageId") String messageId,
            @Param("processedAt") LocalDateTime processedAt,
            @Param("received") InboxMessageStatus received,
            @Param("processed") InboxMessageStatus processed
    );
}
