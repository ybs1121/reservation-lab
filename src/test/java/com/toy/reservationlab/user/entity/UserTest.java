package com.toy.reservationlab.user.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void 사용자를_생성하면_삭제되지_않은_상태다() {
        User user = User.create(
                "user-1",
                "테스트 사용자",
                "010-0000-0000",
                "system"
        );

        assertEquals("user-1", user.getUserId());
        assertEquals("N", user.getDelYn());
        assertFalse(user.isDeleted());
    }

    @Test
    void 사용자를_삭제_표시할_수_있다() {
        User user = User.create(
                "user-1",
                "테스트 사용자",
                "010-0000-0000",
                "system"
        );

        user.markDeleted("user-2");

        assertTrue(user.isDeleted());
        assertEquals("user-2", user.getUpdatedBy());
    }
}
