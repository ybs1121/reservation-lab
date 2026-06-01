package com.toy.reservationlab.restaurant.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservation.service.ReservationService;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import com.toy.reservationlab.restaurant.dto.PopularRestaurantResponse;
import com.toy.reservationlab.restaurant.entity.PopularityPeriodType;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.repository.RestaurantPopularityRepository;
import com.toy.reservationlab.restaurant.repository.RestaurantRepository;
import com.toy.reservationlab.restaurant.service.RestaurantService;
import com.toy.reservationlab.user.repository.UserRepository;
import com.toy.reservationlab.user.service.UserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBatchTest
@SpringBootTest
class PopularRestaurantAggregationJobTest {

    private static final LocalDateTime RUN_AT = LocalDateTime.of(2026, 5, 26, 0, 0);

    private int fixtureSequence;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job popularRestaurantAggregationJob;

    @Autowired
    private RestaurantPopularityRepository restaurantPopularityRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private ReservationSlotRepository reservationSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Autowired
    private UserService userService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        fixtureSequence = 0;
        restaurantPopularityRepository.deleteAll();
        reservationRepository.deleteAll();
        reservationSlotRepository.deleteAll();
        restaurantRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 배치_job을_실행하면_전체기간과_기간별_인기_집계가_저장된다() throws Exception {
        createRestaurant("batch-all-time-restaurant", "전체 기간 인기 식당");
        createRestaurant("batch-last-7-restaurant", "최근 7일 인기 식당");
        createRestaurant("batch-last-30-restaurant", "최근 30일 인기 식당");
        createRestaurant("batch-last-90-restaurant", "최근 90일 인기 식당");
        createReservationFixture("batch-all-time", "batch-all-time-restaurant", RUN_AT.minusDays(100));
        createReservationFixture("batch-all-time-2", "batch-all-time-restaurant", RUN_AT.minusDays(120));
        createReservationFixture("batch-last-7", "batch-last-7-restaurant", RUN_AT.minusDays(1));
        createReservationFixture("batch-last-30", "batch-last-30-restaurant", RUN_AT.minusDays(20));
        createReservationFixture("batch-last-90", "batch-last-90-restaurant", RUN_AT.minusDays(60));

        JobExecution jobExecution = jobLauncher.run(popularRestaurantAggregationJob, jobParameters("periods"));

        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
        assertEquals("batch-all-time-restaurant", getPopularRestaurants(PopularityPeriodType.ALL_TIME).getFirst().restaurantId());
        assertEquals(2, getPopularRestaurants(PopularityPeriodType.ALL_TIME).getFirst().reservationCount());
        assertEquals("batch-last-7-restaurant", getPopularRestaurants(PopularityPeriodType.LAST_7_DAYS).getFirst().restaurantId());
        assertEquals(3, getPopularRestaurants(PopularityPeriodType.LAST_90_DAYS).size());
    }

    @Test
    void 취소_삭제_운영중이_아닌_식당은_집계에서_제외된다() throws Exception {
        createRestaurant("batch-valid-restaurant", "정상 인기 식당");
        createRestaurant("batch-cancelled-restaurant", "취소 예약 식당");
        createRestaurant("batch-deleted-reservation-restaurant", "삭제 예약 식당");
        createRestaurant("batch-closed-restaurant", "닫힌 식당");
        createReservationFixture("batch-valid", "batch-valid-restaurant", RUN_AT.minusDays(1));
        createReservationFixture("batch-cancelled", "batch-cancelled-restaurant", RUN_AT.minusDays(1));
        createReservationFixture("batch-deleted-reservation", "batch-deleted-reservation-restaurant", RUN_AT.minusDays(1));
        createReservationFixture("batch-closed", "batch-closed-restaurant", RUN_AT.minusDays(1));
        reservationService.updateReservationStatus("batch-cancelled-reservation", ReservationStatus.CANCELLED, "system");
        reservationService.deleteReservation("batch-deleted-reservation-reservation", "system");
        restaurantService.updateRestaurant(
                "batch-closed-restaurant",
                "닫힌 식당",
                "서울시 강남구",
                RestaurantStatus.CLOSED,
                "system"
        );

        JobExecution jobExecution = jobLauncher.run(popularRestaurantAggregationJob, jobParameters("exclude"));

        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
        List<PopularRestaurantResponse> responses = getPopularRestaurants(PopularityPeriodType.ALL_TIME);
        assertEquals(1, responses.size());
        assertEquals("batch-valid-restaurant", responses.getFirst().restaurantId());
    }

    @Test
    void 예약_건수가_같으면_식당_등록이_빠른_순서로_정렬된다() throws Exception {
        createRestaurant("batch-old-restaurant", "먼저 등록된 식당");
        createRestaurant("batch-new-restaurant", "나중 등록된 식당");
        moveRestaurantCreatedAt("batch-old-restaurant", RUN_AT.minusDays(10));
        moveRestaurantCreatedAt("batch-new-restaurant", RUN_AT.minusDays(1));
        createReservationFixture("batch-old", "batch-old-restaurant", RUN_AT.minusDays(1));
        createReservationFixture("batch-new", "batch-new-restaurant", RUN_AT.minusDays(1));

        JobExecution jobExecution = jobLauncher.run(popularRestaurantAggregationJob, jobParameters("tie"));

        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
        assertEquals("batch-old-restaurant", getPopularRestaurants(PopularityPeriodType.ALL_TIME).getFirst().restaurantId());
    }

    private JobParameters jobParameters(String testCase) {
        return new JobParametersBuilder()
                .addLocalDateTime("runAt", RUN_AT)
                .addString("rerunId", testCase)
                .toJobParameters();
    }

    private List<PopularRestaurantResponse> getPopularRestaurants(PopularityPeriodType periodType) {
        return restaurantPopularityRepository.findPopularRestaurantsByPeriod(
                periodType,
                RestaurantStatus.OPEN,
                PageRequest.of(0, 100)
        );
    }

    private void createRestaurant(String restaurantId, String name) {
        restaurantService.createRestaurant(
                restaurantId,
                name,
                "서울시 강남구",
                RestaurantStatus.OPEN,
                "system"
        );
    }

    private void createReservationFixture(String prefix, String restaurantId, LocalDateTime reservationCreatedAt) {
        String slotId = prefix + "-slot";
        String userId = prefix + "-user";
        String reservationId = prefix + "-reservation";
        reservationSlotService.createReservationSlot(
                slotId,
                restaurantId,
                LocalDate.now().plusDays(++fixtureSequence),
                "18:00",
                10,
                ReservationSlotStatus.AVAILABLE,
                "system"
        );
        userService.createUser(userId, "배치 테스트 사용자", "010-" + Math.abs(userId.hashCode()), "system");
        reservationService.createReservation(reservationId, slotId, userId, 1, "system");
        moveReservationCreatedAt(reservationId, reservationCreatedAt);
    }

    private void moveReservationCreatedAt(String reservationId, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE reservation SET created_at = ?, updated_at = ? WHERE reservation_id = ?",
                createdAt,
                createdAt,
                reservationId
        );
    }

    private void moveRestaurantCreatedAt(String restaurantId, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE restaurant SET created_at = ?, updated_at = ? WHERE restaurant_id = ?",
                createdAt,
                createdAt,
                restaurantId
        );
    }
}
