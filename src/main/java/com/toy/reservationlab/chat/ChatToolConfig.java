package com.toy.reservationlab.chat;

import com.toy.reservationlab.common.component.BizException;
import java.util.Locale;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도구 실행 중 발생한 예외를 LLM에 돌려주기 전 정화한다.
 * 서비스 레이어는 그대로 {@link BizException}을 던지고, 여기서 원시 에러 코드 대신
 * 사용자용 한국어 메시지로 바꿔 LLM이 자연스러운 한국어 안내를 하도록 한다.
 */
@Configuration
public class ChatToolConfig {

    @Bean
    ToolExecutionExceptionProcessor toolExecutionExceptionProcessor(MessageSource messageSource) {
        return (ToolExecutionException exception) -> {
            Throwable cause = exception.getCause();
            if (cause instanceof BizException bizException) {
                // messages.properties의 코드→한국어 매핑으로 변환한다. 코드 자체는 노출하지 않는다.
                return messageSource.getMessage(
                        bizException.getCode(),
                        bizException.getArgs(),
                        "요청을 처리할 수 없습니다.",
                        Locale.KOREAN
                );
            }
            return "요청을 처리하는 중 오류가 발생했습니다.";
        };
    }
}
