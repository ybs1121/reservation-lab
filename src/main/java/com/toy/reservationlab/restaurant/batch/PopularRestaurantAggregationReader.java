package com.toy.reservationlab.restaurant.batch;

import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.restaurant.entity.PopularityPeriodType;
import com.toy.reservationlab.restaurant.entity.RestaurantStatus;
import com.toy.reservationlab.restaurant.repository.PopularRestaurantAggregationRow;
import com.toy.reservationlab.restaurant.repository.RestaurantPopularityRepository;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemReader;

public class PopularRestaurantAggregationReader implements
        ItemReader<PopularRestaurantAggregationRow>,
        StepExecutionListener {

    private static final List<ReservationStatus> POPULAR_TARGET_STATUSES = List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.NO_SHOW
    );

    private final RestaurantPopularityRepository restaurantPopularityRepository;
    private final PopularityPeriodType periodType;
    private Iterator<PopularRestaurantAggregationRow> iterator;

    public PopularRestaurantAggregationReader(
            RestaurantPopularityRepository restaurantPopularityRepository,
            PopularityPeriodType periodType
    ) {
        this.restaurantPopularityRepository = restaurantPopularityRepository;
        this.periodType = periodType;
    }

    /**
     * Step 시작 시점에 JobParameter의 runAt을 읽어 기간별 조회 범위를 확정한다.
     * Reader는 이후 read() 호출마다 미리 조회한 집계 row를 하나씩 넘긴다.
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        LocalDateTime runAt = stepExecution.getJobParameters().getLocalDateTime("runAt");
        if (runAt == null) {
            throw new IllegalArgumentException("popularRestaurantAggregationJob requires runAt JobParameter");
        }

        LocalDateTime startedAt = periodType.resolveStartedAt(runAt);
        List<PopularRestaurantAggregationRow> rows = restaurantPopularityRepository.findAggregationRows(
                RestaurantStatus.OPEN,
                POPULAR_TARGET_STATUSES,
                startedAt,
                runAt
        );
        this.iterator = rows.iterator();
    }

    @Override
    public PopularRestaurantAggregationRow read() {
        if (iterator == null || !iterator.hasNext()) {
            return null;
        }
        return iterator.next();
    }
}
