package com.toy.reservationlab.chat;

import com.toy.reservationlab.restaurant.dto.RestaurantResponse;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * LLM이 호출할 수 있도록 기존 {@link RestaurantService}를 감싸는 식당 도구 모음이다.
 * 새 비즈니스 로직 없이 기존 서비스 메서드에 위임만 한다.
 */
@Component
@RequiredArgsConstructor
public class RestaurantTools {

    private final RestaurantService restaurantService;

    @Tool(description = """
            등록된 식당 목록을 조회한다.
            각 식당의 ID, 이름, 주소, 상태(OPEN/CLOSED 등), 삭제 여부를 반환한다.
            사용자가 어떤 식당이 있는지 묻거나, 예약에 필요한 식당 ID를 찾아야 할 때 사용한다.""")
    public List<RestaurantResponse> getRestaurants() {
        return restaurantService.getRestaurants().stream()
                .map(RestaurantResponse::from)
                .toList();
    }
}
