package com.toy.reservationlab.reservationhold.service;

import static com.toy.reservationlab.common.component.ErrorCode.RESERVATION_HOLD_REQUEST_NOT_FOUND;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.common.outbox.OutboxEvent;
import com.toy.reservationlab.common.outbox.OutboxEventRepository;
import com.toy.reservationlab.reservationhold.component.ReservationHoldMessageSerializer;
import com.toy.reservationlab.reservationhold.component.ReservationHoldRequestedMessage;
import com.toy.reservationlab.reservationhold.component.ReservationHoldRequestedPayload;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldRequestCreateRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldRequestResponse;
import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequest;
import com.toy.reservationlab.reservationhold.repository.ReservationHoldRequestRepository;
import java.util.UUID;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(name = "reservation-lab.reservation-hold-request.enabled", havingValue = "true")
public class ReservationHoldRequestService {

    private static final String AGGREGATE_TYPE = "RESERVATION_HOLD_REQUEST";
    private static final String EVENT_TYPE = "RESERVATION_HOLD_REQUESTED";
    private static final int PAYLOAD_VERSION = 1;

    private final ReservationHoldRequestRepository reservationHoldRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ReservationHoldMessageSerializer messageSerializer;

    /**
     * 비동기 hold 생성의 첫 단계는 실제 hold를 만들지 않고 요청 상태만 남기는 것이다.
     * 다음 단계에서 이 저장 직후 RabbitMQ로 requestId를 발행해 consumer가 이어서 처리한다.
     */
    @Transactional
    public ReservationHoldRequestResponse createRequest(ReservationHoldRequestCreateRequest request) {
        ReservationHoldRequest holdRequest = ReservationHoldRequest.create(
                UUID.randomUUID().toString(),
                request.slotId(),
                request.userId(),
                request.partySize(),
                request.userId()
        );
        ReservationHoldRequest savedRequest = reservationHoldRequestRepository.save(holdRequest);
        LocalDateTime occurredAt = LocalDateTime.now();
        String eventId = UUID.randomUUID().toString();
        ReservationHoldRequestedMessage message = new ReservationHoldRequestedMessage(
                eventId,
                EVENT_TYPE,
                PAYLOAD_VERSION,
                occurredAt.toString(),
                new ReservationHoldRequestedPayload(
                        savedRequest.getRequestId(),
                        savedRequest.getSlotId(),
                        savedRequest.getUserId(),
                        savedRequest.getPartySize()
                )
        );
        outboxEventRepository.save(OutboxEvent.create(
                eventId,
                AGGREGATE_TYPE,
                savedRequest.getRequestId(),
                EVENT_TYPE,
                PAYLOAD_VERSION,
                messageSerializer.serialize(message),
                occurredAt
        ));
        return ReservationHoldRequestResponse.from(savedRequest);
    }

    /**
     * 비동기 요청은 최종 hold 결과를 즉시 알 수 없으므로 requestId로 현재 처리 상태를 조회한다.
     */
    public ReservationHoldRequestResponse getRequest(String requestId) {
        ReservationHoldRequest request = reservationHoldRequestRepository.findById(requestId)
                .orElseThrow(() -> new BizException(RESERVATION_HOLD_REQUEST_NOT_FOUND));
        return ReservationHoldRequestResponse.from(request);
    }
}
