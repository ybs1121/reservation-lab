package com.toy.reservationlab.reservationhold.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "reservation-lab.reservation-hold.enabled", havingValue = "true")
public class ReservationHoldStore {

    private static final String HOLD_MAP_NAME = "reservation-holds";
    private static final String SLOT_MAP_PREFIX = "reservation-holds:slot:";
    private static final String USER_MAP_PREFIX = "reservation-holds:user:";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // hold 단건과 슬롯/사용자 인덱스를 같은 TTL로 저장해 capacity 계산과 제한 검증에 사용한다.
    public void save(ReservationHoldData hold, long ttlSeconds) {
        String value = toJson(hold);
        holdMap().put(hold.holdId(), value, ttlSeconds, TimeUnit.SECONDS);
        slotMap(hold.slotId()).put(hold.holdId(), value, ttlSeconds, TimeUnit.SECONDS);
        userMap(hold.userId()).put(hold.holdId(), value, ttlSeconds, TimeUnit.SECONDS);
    }

    // 예약 확정과 조회에서 holdId가 아직 유효한지 확인한다.
    public Optional<ReservationHoldData> find(String holdId) {
        return Optional.ofNullable(holdMap().get(holdId)).map(this::fromJson);
    }

    // 같은 사용자가 같은 슬롯을 다시 점유할 때 기존 hold를 반환하기 위해 찾는다.
    public Optional<ReservationHoldData> findByUserIdAndSlotId(String userId, String slotId) {
        return userMap(userId).readAllValues().stream()
                .map(this::fromJson)
                .filter(hold -> hold.slotId().equals(slotId))
                .findFirst();
    }

    // 확정 예약 인원과 합산할 슬롯별 active hold 인원을 계산한다.
    public int sumPartySizeBySlotId(String slotId) {
        return slotMap(slotId).readAllValues().stream()
                .map(this::fromJson)
                .mapToInt(ReservationHoldData::partySize)
                .sum();
    }

    // 사용자별 active hold 최대 개수 정책을 검증할 때 사용한다.
    public int countByUserId(String userId) {
        return userMap(userId).size();
    }

    // 화면에 남은 점유 시간을 표시할 수 있도록 Redis TTL을 초 단위로 반환한다.
    public long getTtlSeconds(String holdId) {
        long ttlMillis = holdMap().remainTimeToLive(holdId);
        if (ttlMillis <= 0) {
            return 0;
        }
        return TimeUnit.MILLISECONDS.toSeconds(ttlMillis);
    }

    // 예약 확정 또는 명시적 해제 시 hold 단건과 인덱스를 함께 제거한다.
    public void delete(ReservationHoldData hold) {
        holdMap().remove(hold.holdId());
        slotMap(hold.slotId()).remove(hold.holdId());
        userMap(hold.userId()).remove(hold.holdId());
    }

    private RMapCache<String, String> holdMap() {
        return redissonClient.getMapCache(HOLD_MAP_NAME);
    }

    private RMapCache<String, String> slotMap(String slotId) {
        return redissonClient.getMapCache(SLOT_MAP_PREFIX + slotId);
    }

    private RMapCache<String, String> userMap(String userId) {
        return redissonClient.getMapCache(USER_MAP_PREFIX + userId);
    }

    private String toJson(ReservationHoldData hold) {
        try {
            return objectMapper.writeValueAsString(hold);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize reservation hold.", exception);
        }
    }

    private ReservationHoldData fromJson(String value) {
        try {
            return objectMapper.readValue(value, ReservationHoldData.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize reservation hold.", exception);
        }
    }
}
