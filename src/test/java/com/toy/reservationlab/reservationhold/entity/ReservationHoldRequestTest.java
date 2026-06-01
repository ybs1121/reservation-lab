package com.toy.reservationlab.reservationhold.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ReservationHoldRequestTest {

    @Test
    void hold_요청을_생성하면_PENDING_상태로_시작한다() {
        ReservationHoldRequest request = ReservationHoldRequest.create(
                "hold-request-1",
                "slot-1",
                "user-1",
                2,
                "user-1"
        );

        assertEquals(ReservationHoldRequestStatus.PENDING, request.getStatus());
        assertEquals(0, request.getRetryCount());
        assertNull(request.getHoldId());
    }

    @Test
    void hold_요청_처리를_시작하면_PROCESSING_상태가_된다() {
        ReservationHoldRequest request = createRequest();

        request.startProcessing("system");

        assertEquals(ReservationHoldRequestStatus.PROCESSING, request.getStatus());
    }

    @Test
    void hold_생성에_성공하면_SUCCEEDED_상태와_holdId를_저장한다() {
        ReservationHoldRequest request = createRequest();

        request.succeed("hold-1", "system");

        assertEquals(ReservationHoldRequestStatus.SUCCEEDED, request.getStatus());
        assertEquals("hold-1", request.getHoldId());
        assertNull(request.getFailureCode());
        assertNull(request.getFailureMessage());
    }

    @Test
    void hold_생성에_실패하면_FAILED_상태와_실패_코드를_저장한다() {
        ReservationHoldRequest request = createRequest();

        request.fail("CAPACITY_EXCEEDED", "system");

        assertEquals(ReservationHoldRequestStatus.FAILED, request.getStatus());
        assertEquals("CAPACITY_EXCEEDED", request.getFailureCode());
        assertEquals(ReservationHoldRequest.DEFAULT_FAILURE_MESSAGE, request.getFailureMessage());
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
}
