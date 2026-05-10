package com.toy.reservationlab.common.config;

import com.toy.reservationlab.common.component.ApiResponse;
import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.common.component.ErrorCode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException exception) {
        String message = getMessage(exception.getCode(), exception.getArgs());
        return ResponseEntity.badRequest().body(ApiResponse.fail(message, exception.getCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        String code = ErrorCode.UNKNOWN_ERROR.getCode();
        String message = getMessage(code, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(message, code));
    }

    private String getMessage(String code, Object[] args) {
        return messageSource.getMessage(code, args, code, Locale.KOREAN);
    }
}
