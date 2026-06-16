package com.toy.reservationlab.chat;

import com.toy.reservationlab.reservation.dto.ReservationResponse;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.service.ReservationService;
import com.toy.reservationlab.reservationslot.dto.ReservationSlotResponse;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * LLM이 호출할 수 있도록 기존 예약/슬롯 서비스를 감싸는 도구 모음이다.
 * 새 비즈니스 로직 없이 기존 서비스 메서드에 위임만 하며, 모든 검증과 실행 책임은
 * 서비스 레이어에 그대로 남는다.
 *
 * <p>이 데모는 인증이 없어 예약 주체를 고정 데모 사용자로 둔다. 따라서 사용자는
 * 자연어에 ID를 넣지 않아도 되고, reservationId는 서버가 UUID로 생성한다.
 */
@Component
@RequiredArgsConstructor
public class ReservationTools {

    // 인증이 없는 데모이므로 예약 주체를 시드된 고정 사용자로 둔다.
    private static final String DEMO_USER_ID = "USR-20260510-000000001";
    // 감사 필드(created_by/updated_by)에 남길 행위자 식별자.
    private static final String CHAT_ACTOR = "CHAT";

    private final ReservationService reservationService;
    private final ReservationSlotService reservationSlotService;

    @Tool(description = """
            특정 식당의 특정 날짜에 예약 가능한 슬롯 목록을 조회한다.
            각 슬롯의 ID, 날짜, 시간, 정원, 상태를 반환한다.
            예약을 생성하기 전에 이 도구로 slotId를 먼저 찾아야 한다.
            restaurantId는 식당 목록 조회로 얻고, 날짜는 오늘 기준 상대 표현을 계산해 넘긴다.""")
    public List<ReservationSlotResponse> getAvailableSlots(
            @ToolParam(description = "슬롯을 조회할 식당의 ID") String restaurantId,
            @ToolParam(description = "조회할 날짜. ISO-8601 형식(yyyy-MM-dd)") String date
    ) {
        return reservationSlotService.getAvailableSlots(restaurantId, LocalDate.parse(date)).stream()
                .map(ReservationSlotResponse::from)
                .toList();
    }

    @Tool(description = """
            예약 ID로 단일 예약을 조회한다.
            슬롯, 사용자, 인원 수, 상태 등 예약 상세 정보를 반환한다.
            사용자가 특정 예약의 상태를 물어볼 때 사용한다.""")
    public Reservation getReservation(
            @ToolParam(description = "조회할 예약의 고유 ID") String reservationId
    ) {
        return reservationService.getReservation(reservationId);
    }

    @Tool(description = """
            현재 사용자의 모든 예약 목록을 조회한다(삭제되지 않은 예약).
            각 예약의 ID, 슬롯, 인원 수, 상태를 반환한다.
            사용자가 "내 예약 보여줘"처럼 자신의 예약을 물어볼 때 사용한다.
            사용자 ID는 시스템이 자동으로 정하므로 묻지 않는다.""")
    public List<ReservationResponse> getMyReservations() {
        return reservationService.getUserReservations(DEMO_USER_ID).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Tool(description = """
            예약 가능한 슬롯에 현재 사용자 이름으로 새 확정 예약을 생성한다.
            slotId와 인원 수만 결정하면 되고, 예약 ID와 사용자 ID는 시스템이 자동으로 채운다.
            slotId는 예약 가능한 슬롯 조회로 먼저 얻어야 한다.
            정원 초과, 인원 수, 슬롯 유효성 검증은 LLM이 아니라 서비스 레이어가 책임진다.""")
    public Reservation createReservation(
            @ToolParam(description = "예약할 슬롯의 ID(예약 가능한 슬롯 조회로 얻은 값)") String slotId,
            @ToolParam(description = "예약 인원 수. 1 이상이어야 한다") int partySize
    ) {
        return reservationService.createReservation(
                UUID.randomUUID().toString(),
                slotId,
                DEMO_USER_ID,
                partySize,
                CHAT_ACTOR
        );
    }

    @Tool(description = """
            예약 ID로 기존 확정 예약을 취소한다.
            내부적으로 예약 상태를 CANCELLED로 전환하며, 잘못된 상태 전환은
            서비스 레이어가 거부한다. 사용자가 예약을 취소하려 할 때 사용한다.""")
    public Reservation cancelReservation(
            @ToolParam(description = "취소할 예약 ID") String reservationId
    ) {
        return reservationService.updateReservationStatus(reservationId, ReservationStatus.CANCELLED, CHAT_ACTOR);
    }
}
