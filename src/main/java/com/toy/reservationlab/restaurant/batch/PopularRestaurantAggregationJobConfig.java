package com.toy.reservationlab.restaurant.batch;

import com.toy.reservationlab.restaurant.component.PopularRestaurantCacheEvictor;
import com.toy.reservationlab.restaurant.entity.PopularityPeriodType;
import com.toy.reservationlab.restaurant.entity.RestaurantPopularity;
import com.toy.reservationlab.restaurant.repository.PopularRestaurantAggregationRow;
import com.toy.reservationlab.restaurant.repository.RestaurantPopularityRepository;
import java.util.List;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class PopularRestaurantAggregationJobConfig {

    private static final int CHUNK_SIZE = 100;
    private static final List<PopularityPeriodType> AGGREGATION_PERIOD_TYPES = List.of(
            PopularityPeriodType.ALL_TIME,
            PopularityPeriodType.LAST_7_DAYS,
            PopularityPeriodType.LAST_30_DAYS,
            PopularityPeriodType.LAST_90_DAYS
    );

    /*
     * 하나의 Job은 "인기 음식점 집계"라는 업무 흐름 전체를 나타낸다.
     * Step을 순서대로 연결해 전체 기간과 기간별 집계를 같은 실행 시점 기준으로 맞춘다.
     */
    @Bean
    public Job popularRestaurantAggregationJob(
            JobRepository jobRepository,
            Step deleteOldRestaurantPopularityStep,
            Step aggregateAllTimeRestaurantPopularityStep,
            Step aggregateLast7DaysRestaurantPopularityStep,
            Step aggregateLast30DaysRestaurantPopularityStep,
            Step aggregateLast90DaysRestaurantPopularityStep,
            Step evictPopularRestaurantCacheStep
    ) {
        return new JobBuilder("popularRestaurantAggregationJob", jobRepository)
                .start(deleteOldRestaurantPopularityStep)
                .next(aggregateAllTimeRestaurantPopularityStep)
                .next(aggregateLast7DaysRestaurantPopularityStep)
                .next(aggregateLast30DaysRestaurantPopularityStep)
                .next(aggregateLast90DaysRestaurantPopularityStep)
                .next(evictPopularRestaurantCacheStep)
                .build();
    }

    /*
     * Tasklet Step은 여러 item을 읽고 쓰는 작업보다 단일 명령에 가깝다.
     * 실제 삭제 로직은 집계 저장 Step이 준비된 뒤 연결한다.
     */
    @Bean
    public Step deleteOldRestaurantPopularityStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RestaurantPopularityRepository restaurantPopularityRepository
    ) {
        return new StepBuilder("deleteOldRestaurantPopularityStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    restaurantPopularityRepository.deleteByPeriodTypeIn(AGGREGATION_PERIOD_TYPES);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /*
     * 전체 기간 집계 Step이다.
     * Reader가 전체 기간 예약 건수를 읽고, Processor가 집계 entity로 변환하고, Writer가 저장한다.
     */
    @Bean
    public Step aggregateAllTimeRestaurantPopularityStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RestaurantPopularityRepository restaurantPopularityRepository
    ) {
        return aggregationStep(
                "aggregateAllTimeRestaurantPopularityStep",
                PopularityPeriodType.ALL_TIME,
                jobRepository,
                transactionManager,
                restaurantPopularityRepository
        );
    }

    /*
     * 최근 7일 집계 Step의 자리다.
     * 날짜 기준은 PopularityPeriodType.LAST_7_DAYS enum을 사용하게 된다.
     */
    @Bean
    public Step aggregateLast7DaysRestaurantPopularityStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RestaurantPopularityRepository restaurantPopularityRepository
    ) {
        return aggregationStep(
                "aggregateLast7DaysRestaurantPopularityStep",
                PopularityPeriodType.LAST_7_DAYS,
                jobRepository,
                transactionManager,
                restaurantPopularityRepository
        );
    }

    /*
     * 최근 30일 집계 Step의 자리다.
     * 같은 집계 구조를 기간 정책만 바꿔 재사용하는지 확인하는 학습 지점이다.
     */
    @Bean
    public Step aggregateLast30DaysRestaurantPopularityStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RestaurantPopularityRepository restaurantPopularityRepository
    ) {
        return aggregationStep(
                "aggregateLast30DaysRestaurantPopularityStep",
                PopularityPeriodType.LAST_30_DAYS,
                jobRepository,
                transactionManager,
                restaurantPopularityRepository
        );
    }

    /*
     * 최근 90일 집계 Step의 자리다.
     * 기간이 늘어나도 enum과 Step 이름으로 역할이 드러나도록 둔다.
     */
    @Bean
    public Step aggregateLast90DaysRestaurantPopularityStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RestaurantPopularityRepository restaurantPopularityRepository
    ) {
        return aggregationStep(
                "aggregateLast90DaysRestaurantPopularityStep",
                PopularityPeriodType.LAST_90_DAYS,
                jobRepository,
                transactionManager,
                restaurantPopularityRepository
        );
    }

    /*
     * 집계 완료 후 캐시를 비우는 후처리 Step이다.
     * 집계 테이블이 새 값으로 교체됐으므로 기존 Redis 캐시는 더 이상 신뢰하지 않는다.
     */
    @Bean
    public Step evictPopularRestaurantCacheStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            PopularRestaurantCacheEvictor popularRestaurantCacheEvictor
    ) {
        return new StepBuilder("evictPopularRestaurantCacheStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    popularRestaurantCacheEvictor.evictAll();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    private Step aggregationStep(
            String stepName,
            PopularityPeriodType periodType,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RestaurantPopularityRepository restaurantPopularityRepository
    ) {
        PopularRestaurantAggregationReader reader = new PopularRestaurantAggregationReader(
                restaurantPopularityRepository,
                periodType
        );
        RestaurantPopularityItemProcessor processor = new RestaurantPopularityItemProcessor(periodType);
        RestaurantPopularityItemWriter writer = new RestaurantPopularityItemWriter(restaurantPopularityRepository);

        return new StepBuilder(stepName, jobRepository)
                .<PopularRestaurantAggregationRow, RestaurantPopularity>chunk(CHUNK_SIZE)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .transactionManager(transactionManager)
                .listener(reader)
                .listener(processor)
                .build();
    }
}
