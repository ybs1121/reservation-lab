package com.toy.reservationlab.restaurant.batch;

import com.toy.reservationlab.restaurant.entity.PopularityPeriodType;
import com.toy.reservationlab.restaurant.entity.RestaurantPopularity;
import com.toy.reservationlab.restaurant.repository.PopularRestaurantAggregationRow;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;

public class RestaurantPopularityItemProcessor implements
        ItemProcessor<PopularRestaurantAggregationRow, RestaurantPopularity>,
        StepExecutionListener {

    private static final String CREATED_BY = "BATCH";

    private final PopularityPeriodType periodType;
    private LocalDateTime runAt;

    public RestaurantPopularityItemProcessor(PopularityPeriodType periodType) {
        this.periodType = periodType;
    }

    /**
     * Processor는 Reader가 만든 집계 row를 저장 가능한 읽기 모델 entity로 변환한다.
     * aggregatedAt은 JobParameter runAt으로 고정해 같은 Job 실행 결과임을 드러낸다.
     */
    @Override
    public RestaurantPopularity process(PopularRestaurantAggregationRow item) {
        return RestaurantPopularity.create(
                UUID.randomUUID().toString(),
                item.restaurantId(),
                periodType,
                item.reservationCount(),
                runAt,
                CREATED_BY
        );
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.runAt = stepExecution.getJobParameters().getLocalDateTime("runAt");
        if (runAt == null) {
            throw new IllegalArgumentException("popularRestaurantAggregationJob requires runAt JobParameter");
        }
    }
}
