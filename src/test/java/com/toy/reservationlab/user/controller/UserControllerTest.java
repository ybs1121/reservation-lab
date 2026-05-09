package com.toy.reservationlab.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toy.reservationlab.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    void 사용자를_생성하면_사용자_응답을_반환한다() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-controller-1",
                                  "name": "테스트 사용자",
                                  "phone": "010-2000-0001",
                                  "createdBy": "system"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("user-controller-1"))
                .andExpect(jsonPath("$.data.name").value("테스트 사용자"))
                .andExpect(jsonPath("$.data.phone").value("010-2000-0001"))
                .andExpect(jsonPath("$.data.delYn").value("N"));
    }

    @Test
    void 사용자를_조회하면_사용자_응답을_반환한다() throws Exception {
        userService.createUser(
                "user-controller-2",
                "조회 사용자",
                "010-2000-0002",
                "system"
        );

        mockMvc.perform(get("/users/user-controller-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("user-controller-2"))
                .andExpect(jsonPath("$.data.name").value("조회 사용자"));
    }

    @Test
    void 존재하지_않는_사용자를_조회하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/users/not-found-user"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.code").value("USR00001"));
    }

    @Test
    void 사용자를_수정하면_사용자_응답을_반환한다() throws Exception {
        userService.createUser(
                "user-controller-3",
                "수정 전 사용자",
                "010-2000-0003",
                "system"
        );

        mockMvc.perform(put("/users/user-controller-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "수정 후 사용자",
                                  "phone": "010-2000-3003",
                                  "updatedBy": "user-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("user-controller-3"))
                .andExpect(jsonPath("$.data.name").value("수정 후 사용자"))
                .andExpect(jsonPath("$.data.phone").value("010-2000-3003"));
    }

    @Test
    void 사용자를_삭제하면_삭제된_사용자_응답을_반환한다() throws Exception {
        userService.createUser(
                "user-controller-4",
                "삭제 사용자",
                "010-2000-0004",
                "system"
        );

        mockMvc.perform(delete("/users/user-controller-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "updatedBy": "user-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("user-controller-4"))
                .andExpect(jsonPath("$.data.delYn").value("Y"));
    }
}
