package com.toy.reservationlab.reservation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toy.reservationlab.reservation.service.ReservationService;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import com.toy.reservationlab.user.service.UserService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Autowired
    private ReservationService reservationService;

    @Test
    void 예약을_생성하면_예약_응답을_반환한다() throws Exception {
        createUser("reservation-controller-user-1", "010-4000-0001");
        createSlot("reservation-controller-restaurant-1", "reservation-controller-slot-1", 4);

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": "reservation-controller-1",
                                  "slotId": "reservation-controller-slot-1",
                                  "userId": "reservation-controller-user-1",
                                  "partySize": 2,
                                  "createdBy": "reservation-controller-user-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value("reservation-controller-1"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.delYn").value("N"));
    }

    @Test
    void 예약을_조회하면_예약_응답을_반환한다() throws Exception {
        createUser("reservation-controller-user-2", "010-4000-0002");
        createSlot("reservation-controller-restaurant-2", "reservation-controller-slot-2", 4);
        reservationService.createReservation(
                "reservation-controller-2",
                "reservation-controller-slot-2",
                "reservation-controller-user-2",
                2,
                "reservation-controller-user-2"
        );

        mockMvc.perform(get("/reservations/reservation-controller-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value("reservation-controller-2"))
                .andExpect(jsonPath("$.data.partySize").value(2));
    }

    @Test
    void 예약_상태를_수정하면_예약_응답을_반환한다() throws Exception {
        createUser("reservation-controller-user-3", "010-4000-0003");
        createSlot("reservation-controller-restaurant-3", "reservation-controller-slot-3", 4);
        reservationService.createReservation(
                "reservation-controller-3",
                "reservation-controller-slot-3",
                "reservation-controller-user-3",
                2,
                "reservation-controller-user-3"
        );

        mockMvc.perform(put("/reservations/reservation-controller-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CANCELLED",
                                  "updatedBy": "system"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value("reservation-controller-3"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void 예약을_삭제하면_삭제된_예약_응답을_반환한다() throws Exception {
        createUser("reservation-controller-user-4", "010-4000-0004");
        createSlot("reservation-controller-restaurant-4", "reservation-controller-slot-4", 4);
        reservationService.createReservation(
                "reservation-controller-4",
                "reservation-controller-slot-4",
                "reservation-controller-user-4",
                2,
                "reservation-controller-user-4"
        );

        mockMvc.perform(delete("/reservations/reservation-controller-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "updatedBy": "system"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value("reservation-controller-4"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.delYn").value("Y"));
    }

    private void createUser(String userId, String phone) {
        userService.createUser(userId, "예약 사용자", phone, "system");
    }

    private void createSlot(String restaurantId, String slotId, int capacity) {
        restaurantService.createRestaurant(
                restaurantId,
                "예약 식당",
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "system"
        );
        reservationSlotService.createReservationSlot(
                slotId,
                restaurantId,
                LocalDate.now().plusDays(1),
                "18:00",
                capacity,
                ReservationSlotStatus.AVAILABLE,
                "system"
        );
    }
}
