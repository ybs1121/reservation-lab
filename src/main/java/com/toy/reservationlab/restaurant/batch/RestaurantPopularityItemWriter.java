package com.toy.reservationlab.restaurant.batch;

import com.toy.reservationlab.restaurant.entity.RestaurantPopularity;
import com.toy.reservationlab.restaurant.repository.RestaurantPopularityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

@RequiredArgsConstructor
public class RestaurantPopularityItemWriter implements ItemWriter<RestaurantPopularity> {

    private final RestaurantPopularityRepository restaurantPopularityRepository;

    /**
     * Writer는 chunk 단위로 모인 집계 entity를 저장한다.
     * chunk 하나가 하나의 트랜잭션 경계 안에서 저장되므로 실패 시 해당 chunk가 rollback된다.
     */
    @Override
    public void write(Chunk<? extends RestaurantPopularity> chunk) {
        restaurantPopularityRepository.saveAll(chunk.getItems());
    }
}
