package com.toy.reservationlab.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.toy.reservationlab.common.component.ApiResponse;
import com.toy.reservationlab.common.component.BizException;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @Test
    void 비즈니스_예외는_400과_실패_응답을_반환한다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource());

        ResponseEntity<ApiResponse<Void>> response = handler.handleBizException(new BizException("COM00002"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().success());
        assertEquals("비즈니스 오류", response.getBody().message());
        assertEquals("COM00002", response.getBody().code());
    }

    @Test
    void 알수없는_예외는_500과_실패_응답을_반환한다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource());

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(new RuntimeException());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().success());
        assertEquals("알 수 없는 오류가 발생했습니다.", response.getBody().message());
        assertEquals("COM00001", response.getBody().code());
    }

    private StaticMessageSource messageSource() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("COM00001", Locale.KOREAN, "알 수 없는 오류가 발생했습니다.");
        messageSource.addMessage("COM00002", Locale.KOREAN, "비즈니스 오류");
        return messageSource;
    }
}
