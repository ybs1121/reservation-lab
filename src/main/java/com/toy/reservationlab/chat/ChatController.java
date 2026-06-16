package com.toy.reservationlab.chat;

import com.toy.reservationlab.common.component.ApiResponse;
import java.time.LocalDate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자연어 예약 요청을 받아 LLM이 적절한 예약 도구를 호출하도록 위임하는 엔드포인트다.
 * LLM은 "어떤 함수를 어떤 인자로 부를지"만 결정하고, 실행과 안전성은 기존 서비스가 책임진다.
 */
@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final ReservationTools reservationTools;
    private final RestaurantTools restaurantTools;

    public ChatController(
            ChatClient.Builder chatClientBuilder,
            ReservationTools reservationTools,
            RestaurantTools restaurantTools
    ) {
        this.chatClient = chatClientBuilder.build();
        this.reservationTools = reservationTools;
        this.restaurantTools = restaurantTools;
    }

    // charset 미명시 시 일부 클라이언트(PowerShell 5.1 Invoke-RestMethod)가 UTF-8을
    // ISO-8859-1로 디코딩해 한글이 깨진다. 응답 charset을 명시해 디코딩을 강제한다.
    @PostMapping(produces = "application/json;charset=UTF-8")
    public ApiResponse<String> chat(@RequestBody ChatRequest request) {
        String systemPrompt = """
                당신은 식당 예약 시스템의 어시스턴트다.
                반드시 한국어로만 답변하라.
                오늘 날짜는 %s (ISO-8601)다. "내일", "이번 주 금요일", "다음 주" 같은 상대
                날짜는 이 값을 기준으로 실제 날짜로 계산하라.
                사용자가 예약을 조회/생성/취소하거나 식당 목록을 보려 하면 알맞은 도구를
                정확한 인자로 호출하라. 어떤 함수를 어떤 인자로 부를지만 결정하고, 모든
                검증과 실행은 백엔드 서비스가 책임진다.
                예약을 생성할 때는 다음 순서로 도구를 연쇄 호출하라:
                1) 식당 목록 조회로 사용자가 말한 식당의 restaurantId를 찾는다.
                2) 예약 가능한 슬롯 조회로 해당 식당/날짜의 slotId를 찾는다.
                3) 그 slotId와 인원 수로 예약을 생성한다.
                사용자 ID, 예약 ID, 생성자 같은 식별자는 시스템이 자동으로 채우므로 사용자에게
                묻지 마라. 식당이나 슬롯을 특정할 수 없을 때(예: 식당명 모호, 해당 날짜 슬롯 없음)
                에만 사용자에게 되물어라. 식별자를 임의로 지어내지 마라.
                내부 에러 코드나 식별자(예: RSV00001)를 그대로 노출하지 말고, 자연스러운
                한국어로 상황을 설명하라.""".formatted(LocalDate.now());

        log.info("request : {}", request);

        String answer;
        try {
            answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(request.message())
                    .tools(reservationTools, restaurantTools)
                    .call()
                    .content();
        } catch (Exception e) {
            // 모델 호출/도구 왕복 실패 시 전체 스택트레이스(Caused by 포함)를 남겨 원인 파악을 돕고,
            // 사용자에게는 친절한 한국어 메시지로 응답한다.
            log.error("chat failed. request={}", request, e);
            return new ApiResponse<>(false, null, "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", "CHAT_FAILED");
        }

        log.info("answer : {}", answer);

        return ApiResponse.success(answer);
    }

    public record ChatRequest(String message) {
    }
}
