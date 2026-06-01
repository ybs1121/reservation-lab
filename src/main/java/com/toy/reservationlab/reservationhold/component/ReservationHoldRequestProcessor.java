package com.toy.reservationlab.reservationhold.component;

import static com.toy.reservationlab.common.component.ErrorCode.UNKNOWN_ERROR;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldCreateRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldResponse;
import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequest;
import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequestStatus;
import com.toy.reservationlab.reservationhold.repository.ReservationHoldRequestRepository;
import com.toy.reservationlab.reservationhold.service.ReservationHoldService;
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
public class ReservationHoldRequestProcessor {

    private static final String SYSTEM_USER = "SYSTEM";
    private static final int SYSTEM_FAILURE_MAX_RETRY_COUNT = 1;

    private final ReservationHoldRequestRepository reservationHoldRequestRepository;
    private final ReservationHoldService reservationHoldService;

    /**
     * 큐 메시지를 실제 hold 생성 처리로 바꾸는 핵심 흐름이다.
     * 요청 row 상태를 먼저 PROCESSING으로 남기고, 기존 동기 hold 생성 로직을 재사용한다.
     */
    @Transactional
    public void process(String requestId) {
        reservationHoldRequestRepository.findById(requestId)
                .ifPresent(this::process);
    }

    private void process(ReservationHoldRequest request) {
        if (isAlreadyFinished(request)) {
            return;
        }
        request.startProcessing(SYSTEM_USER);
        tryCreateHold(request);
    }

    private void tryCreateHold(ReservationHoldRequest request) {
        try {
            ReservationHoldResponse response = reservationHoldService.createHold(toCreateRequest(request));
            request.succeed(response.holdId(), SYSTEM_USER);
        } catch (BizException e) {
            request.fail(e.getCode(), SYSTEM_USER);
        } catch (RuntimeException e) {
            retrySystemFailure(request);
        }
    }

    /**
     * 비즈니스 실패는 재시도해도 같은 결과일 가능성이 높지만, 시스템 실패는 한 번 더 시도한다.
     * 두 번째 시스템 실패까지 발생하면 사용자에게는 공통 실패 메시지를 보여주고 개발자용 코드는 COM00001로 남긴다.
     */
    private void retrySystemFailure(ReservationHoldRequest request) {
        if (request.getRetryCount() >= SYSTEM_FAILURE_MAX_RETRY_COUNT) {
            request.fail(UNKNOWN_ERROR.getCode(), SYSTEM_USER);
            return;
        }
        request.increaseRetryCount(SYSTEM_USER);
        tryCreateHold(request);
    }

    private boolean isAlreadyFinished(ReservationHoldRequest request) {
        return request.getStatus() == ReservationHoldRequestStatus.SUCCEEDED
                || request.getStatus() == ReservationHoldRequestStatus.FAILED;
    }

    private ReservationHoldCreateRequest toCreateRequest(ReservationHoldRequest request) {
        return new ReservationHoldCreateRequest(
                request.getSlotId(),
                request.getUserId(),
                request.getPartySize()
        );
    }
}
