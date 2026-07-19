package com.toy.reservationlab.common.component;

import static com.toy.reservationlab.common.component.ErrorCode.LOCK_ACQUIRE_FAILED;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "reservation-lab.distributed-lock.enabled", havingValue = "true")
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final DistributedLockKeyParser keyParser;

    @Around("@annotation(com.toy.reservationlab.common.component.DistributedLock)")
    public Object executeWithLock(ProceedingJoinPoint joinPoint) throws Throwable {
        DistributedLock distributedLock = getDistributedLock(joinPoint);
        RLock lock = createLock(joinPoint, distributedLock);
        boolean acquired = tryLock(lock, distributedLock);
        if (!acquired) {
            throw new BizException(LOCK_ACQUIRE_FAILED);
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 실제 키를 정렬해 호출부의 선언 순서와 관계없이 같은 순서로 락을 획득한다.
     * 키가 여러 개면 MultiLock이 일부 획득 후 실패한 락까지 함께 정리한다.
     */
    private RLock createLock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) {
        List<RLock> locks = Arrays.stream(distributedLock.keys())
                .map(keyExpression -> keyParser.parse(joinPoint, keyExpression))
                .distinct()
                .sorted()
                .map(redissonClient::getLock)
                .toList();

        if (locks.size() == 1) {
            return locks.getFirst();
        }
        return redissonClient.getMultiLock(locks.toArray(RLock[]::new));
    }

    private boolean tryLock(RLock lock, DistributedLock distributedLock) {
        try {
            return lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(LOCK_ACQUIRE_FAILED);
        }
    }

    private DistributedLock getDistributedLock(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        return method.getAnnotation(DistributedLock.class);
    }
}
