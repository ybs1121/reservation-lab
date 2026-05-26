package com.toy.reservationlab.restaurant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservation.service.ReservationService;
import com.toy.reservationlab.reservationslot.entity.ReservationSlot;
import com.toy.reservationlab.reservationslot.entity.ReservationSlotStatus;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.reservationslot.service.ReservationSlotService;
import com.toy.reservationlab.restaurant.component.PopularRestaurantCacheEvictor;
import com.toy.reservationlab.restaurant.dto.PopularRestaurantsResponse;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.repository.RestaurantRepository;
import com.toy.reservationlab.user.repository.UserRepository;
import com.toy.reservationlab.user.service.UserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "reservation-lab.popular-restaurant-cache.enabled=true"
})
class PopularRestaurantCacheServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private PopularRestaurantService popularRestaurantService;

    @Autowired
    private PopularRestaurantCacheEvictor popularRestaurantCacheEvictor;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReservationSlotService reservationSlotService;

    @Autowired
    private UserService userService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private ReservationSlotRepository reservationSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        popularRestaurantCacheEvictor.evictAll();
        reservationRepository.deleteAll();
        reservationSlotRepository.deleteAll();
        restaurantRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 인기_음식점은_전체기간과_최근기준을_함께_반환한다() {
        createRestaurant("popular-all-time-restaurant", "전체 인기 식당");
        createRestaurant("popular-recent-restaurant", "최근 인기 식당");
        createSlot("popular-all-time-slot", "popular-all-time-restaurant", 3);
        createSlot("popular-recent-slot", "popular-recent-restaurant", 3);
        createUser("popular-user-1", "010-6100-0001");
        createUser("popular-user-2", "010-6100-0002");
        createUser("popular-user-3", "010-6100-0003");
        reservationService.createReservation("popular-old-reservation-1", "popular-all-time-slot", "popular-user-1", 1, "system");
        reservationService.createReservation("popular-old-reservation-2", "popular-all-time-slot", "popular-user-2", 1, "system");
        reservationService.createReservation("popular-recent-reservation-1", "popular-recent-slot", "popular-user-3", 1, "system");
        moveReservationCreatedAt("popular-old-reservation-1", LocalDateTime.now().minusDays(10));
        moveReservationCreatedAt("popular-old-reservation-2", LocalDateTime.now().minusDays(10));

        PopularRestaurantsResponse response = popularRestaurantService.getPopularRestaurants(10, 7);

        assertEquals("popular-all-time-restaurant", response.allTime().getFirst().restaurantId());
        assertEquals(2, response.allTime().getFirst().reservationCount());
        assertEquals("popular-recent-restaurant", response.recent().getFirst().restaurantId());
        assertEquals(1, response.recent().getFirst().reservationCount());
    }

    @Test
    void 같은_조건으로_다시_조회하면_Redis_캐시_응답을_반환한다() {
        createRestaurant("popular-cache-old-restaurant", "기존 인기 식당");
        createRestaurant("popular-cache-new-restaurant", "새 인기 식당");
        createSlot("popular-cache-old-slot", "popular-cache-old-restaurant", 3);
        reservationSlotRepository.save(ReservationSlot.create(
                "popular-cache-new-slot",
                "popular-cache-new-restaurant",
                LocalDate.now().plusDays(1),
                "19:00",
                3,
                ReservationSlotStatus.AVAILABLE,
                "system"
        ));
        createUser("popular-cache-user-1", "010-6200-0001");
        reservationService.createReservation("popular-cache-old-reservation", "popular-cache-old-slot", "popular-cache-user-1", 1, "system");

        PopularRestaurantsResponse first = popularRestaurantService.getPopularRestaurants(10, 7);
        reservationRepository.save(Reservation.create(
                "popular-cache-new-reservation-1",
                "popular-cache-new-slot",
                "popular-cache-user-1",
                1,
                ReservationStatus.CONFIRMED,
                "system"
        ));
        reservationRepository.save(Reservation.create(
                "popular-cache-new-reservation-2",
                "popular-cache-new-slot",
                "popular-cache-user-1",
                1,
                ReservationStatus.CONFIRMED,
                "system"
        ));
        reservationRepository.flush();

        PopularRestaurantsResponse second = popularRestaurantService.getPopularRestaurants(10, 7);
        popularRestaurantCacheEvictor.evictAll();
        PopularRestaurantsResponse afterEvict = popularRestaurantService.getPopularRestaurants(10, 7);

        assertEquals("popular-cache-old-restaurant", first.allTime().getFirst().restaurantId());
        assertEquals("popular-cache-old-restaurant", second.allTime().getFirst().restaurantId());
        assertEquals("popular-cache-new-restaurant", afterEvict.allTime().getFirst().restaurantId());
    }

    @Test
    void 예약이_생성되면_인기_음식점_캐시를_무효화한다() {
        createRestaurant("popular-evict-old-restaurant", "기존 인기 식당");
        createRestaurant("popular-evict-new-restaurant", "새 인기 식당");
        createSlot("popular-evict-old-slot", "popular-evict-old-restaurant", 3);
        createSlot("popular-evict-new-slot", "popular-evict-new-restaurant", 3);
        createUser("popular-evict-user-1", "010-6300-0001");
        createUser("popular-evict-user-2", "010-6300-0002");
        createUser("popular-evict-user-3", "010-6300-0003");
        reservationService.createReservation("popular-evict-old-reservation", "popular-evict-old-slot", "popular-evict-user-1", 1, "system");
        popularRestaurantService.getPopularRestaurants(10, 7);

        reservationService.createReservation("popular-evict-new-reservation-1", "popular-evict-new-slot", "popular-evict-user-2", 1, "system");
        reservationService.createReservation("popular-evict-new-reservation-2", "popular-evict-new-slot", "popular-evict-user-3", 1, "system");

        PopularRestaurantsResponse response = popularRestaurantService.getPopularRestaurants(10, 7);

        assertEquals("popular-evict-new-restaurant", response.allTime().getFirst().restaurantId());
        assertEquals(2, response.allTime().getFirst().reservationCount());
    }

    @Test
    void 식당이_수정되면_인기_음식점_캐시를_무효화한다() {
        createRestaurant("popular-update-restaurant", "수정 전 인기 식당");
        createSlot("popular-update-slot", "popular-update-restaurant", 1);
        createUser("popular-update-user-1", "010-6400-0001");
        reservationService.createReservation("popular-update-reservation", "popular-update-slot", "popular-update-user-1", 1, "system");
        popularRestaurantService.getPopularRestaurants(10, 7);

        restaurantService.updateRestaurant(
                "popular-update-restaurant",
                "수정 후 인기 식당",
                "서울시 마포구",
                RestaurantStatus.OPEN,
                "system"
        );
        PopularRestaurantsResponse response = popularRestaurantService.getPopularRestaurants(10, 7);

        assertEquals("수정 후 인기 식당", response.allTime().getFirst().name());
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

    private void createSlot(String slotId, String restaurantId, int capacity) {
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

    private void createUser(String userId, String phone) {
        userService.createUser(userId, "인기 식당 사용자", phone, "system");
    }

    private void moveReservationCreatedAt(String reservationId, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "UPDATE reservation SET created_at = ?, updated_at = ? WHERE reservation_id = ?",
                createdAt,
                createdAt,
                reservationId
        );
    }
}
