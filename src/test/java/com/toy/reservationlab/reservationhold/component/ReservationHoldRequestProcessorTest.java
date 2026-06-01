package com.toy.reservationlab.reservationhold.component;

import static com.toy.reservationlab.common.component.ErrorCode.CAPACITY_EXCEEDED;
import static com.toy.reservationlab.common.component.ErrorCode.UNKNOWN_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldCreateRequest;
import com.toy.reservationlab.reservationhold.dto.ReservationHoldResponse;
import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequest;
import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequestStatus;
import com.toy.reservationlab.reservationhold.repository.ReservationHoldRequestRepository;
import com.toy.reservationlab.reservationhold.service.ReservationHoldService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationHoldRequestProcessorTest {

    @Mock
    private ReservationHoldRequestRepository reservationHoldRequestRepository;

    @Mock
    private ReservationHoldService reservationHoldService;

    @InjectMocks
    private ReservationHoldRequestProcessor reservationHoldRequestProcessor;

    @Test
    void hold_생성에_성공하면_요청을_SUCCEEDED로_변경하고_holdId를_저장한다() {
        ReservationHoldRequest request = createRequest();
        when(reservationHoldRequestRepository.findById("hold-request-1")).thenReturn(Optional.of(request));
        when(reservationHoldService.createHold(createHoldRequest()))
                .thenReturn(new ReservationHoldResponse("hold-1", "slot-1", "user-1", 2, 300));

        reservationHoldRequestProcessor.process("hold-request-1");

        assertThat(request.getStatus()).isEqualTo(ReservationHoldRequestStatus.SUCCEEDED);
        assertThat(request.getHoldId()).isEqualTo("hold-1");
        assertThat(request.getRetryCount()).isZero();
    }

    @Test
    void 비즈니스_실패는_재시도하지_않고_FAILED로_저장한다() {
        ReservationHoldRequest request = createRequest();
        when(reservationHoldRequestRepository.findById("hold-request-1")).thenReturn(Optional.of(request));
        when(reservationHoldService.createHold(createHoldRequest()))
                .thenThrow(new BizException(CAPACITY_EXCEEDED));

        reservationHoldRequestProcessor.process("hold-request-1");

        assertThat(request.getStatus()).isEqualTo(ReservationHoldRequestStatus.FAILED);
        assertThat(request.getFailureCode()).isEqualTo(CAPACITY_EXCEEDED.getCode());
        assertThat(request.getRetryCount()).isZero();
    }

    @Test
    void 시스템_실패가_한_번_발생하면_재시도하고_성공_결과를_저장한다() {
        ReservationHoldRequest request = createRequest();
        when(reservationHoldRequestRepository.findById("hold-request-1")).thenReturn(Optional.of(request));
        when(reservationHoldService.createHold(createHoldRequest()))
                .thenThrow(new RuntimeException("temporary failure"))
                .thenReturn(new ReservationHoldResponse("hold-1", "slot-1", "user-1", 2, 300));

        reservationHoldRequestProcessor.process("hold-request-1");

        assertThat(request.getStatus()).isEqualTo(ReservationHoldRequestStatus.SUCCEEDED);
        assertThat(request.getHoldId()).isEqualTo("hold-1");
        assertThat(request.getRetryCount()).isEqualTo(1);
    }

    @Test
    void 시스템_실패가_재시도_후에도_발생하면_FAILED로_저장한다() {
        ReservationHoldRequest request = createRequest();
        when(reservationHoldRequestRepository.findById("hold-request-1")).thenReturn(Optional.of(request));
        when(reservationHoldService.createHold(createHoldRequest()))
                .thenThrow(new RuntimeException("temporary failure"))
                .thenThrow(new RuntimeException("second failure"));

        reservationHoldRequestProcessor.process("hold-request-1");

        assertThat(request.getStatus()).isEqualTo(ReservationHoldRequestStatus.FAILED);
        assertThat(request.getFailureCode()).isEqualTo(UNKNOWN_ERROR.getCode());
        assertThat(request.getRetryCount()).isEqualTo(1);
    }

    @Test
    void 이미_완료된_요청은_다시_처리하지_않는다() {
        ReservationHoldRequest request = createRequest();
        request.succeed("hold-1", "SYSTEM");
        when(reservationHoldRequestRepository.findById("hold-request-1")).thenReturn(Optional.of(request));

        reservationHoldRequestProcessor.process("hold-request-1");

        verify(reservationHoldService, never()).createHold(createHoldRequest());
    }

    private ReservationHoldRequest createRequest() {
        return ReservationHoldRequest.create(
                "hold-request-1",
                "slot-1",
                "user-1",
                2,
                "user-1"
        );
    }

    private ReservationHoldCreateRequest createHoldRequest() {
        return new ReservationHoldCreateRequest("slot-1", "user-1", 2);
    }
}
