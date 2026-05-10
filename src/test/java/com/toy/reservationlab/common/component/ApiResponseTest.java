package com.toy.reservationlab.common.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void 성공_응답은_데이터를_가진다() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertTrue(response.success());
        assertEquals("ok", response.data());
        assertNull(response.message());
        assertNull(response.code());
    }

    @Test
    void 실패_응답은_메시지와_코드를_가진다() {
        ApiResponse<Void> response = ApiResponse.fail("에러", "COM00002");

        assertFalse(response.success());
        assertNull(response.data());
        assertEquals("에러", response.message());
        assertEquals("COM00002", response.code());
    }
}
