package com.toy.reservationlab.reservationhold.component;

import com.toy.reservationlab.common.inbox.InboxMessageRepository;
import com.toy.reservationlab.common.inbox.InboxMessageStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("""
        '${reservation-lab.reservation-hold-request.enabled:false}' == 'true'
        && '${reservation-lab.reservation-hold.enabled:false}' == 'true'
        """)
public class ReservationHoldInboxHandler {

    private final InboxMessageRepository inboxMessageRepository;
    private final ReservationHoldRequestProcessor reservationHoldRequestProcessor;

    @Transactional
    public void handle(ReservationHoldRequestedMessage message) {
        LocalDateTime receivedAt = LocalDateTime.now();
        int inserted = inboxMessageRepository.insertIfAbsent(
                message.messageId(),
                message.payload().requestId(),
                message.eventType(),
                receivedAt
        );
        if (inserted == 0) {
            return;
        }

        reservationHoldRequestProcessor.process(message.payload().requestId());
        inboxMessageRepository.markProcessed(
                message.messageId(),
                LocalDateTime.now(),
                InboxMessageStatus.RECEIVED,
                InboxMessageStatus.PROCESSED
        );
    }
}
