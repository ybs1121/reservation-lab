package com.toy.reservationlab.common.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

class DistributedLockAspectTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final DistributedLockAspect aspect = new DistributedLockAspect(
            redissonClient,
            new DistributedLockKeyParser()
    );

    @Test
    void 여러_락_키를_중복_제거하고_정렬한_뒤_MultiLock으로_실행한다() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("executeWithMultipleLocks");
        RLock firstLock = mock(RLock.class);
        RLock secondLock = mock(RLock.class);
        RLock thirdLock = mock(RLock.class);
        RLock multiLock = mock(RLock.class);

        when(redissonClient.getLock("lock:a")).thenReturn(firstLock);
        when(redissonClient.getLock("lock:b")).thenReturn(secondLock);
        when(redissonClient.getLock("lock:c")).thenReturn(thirdLock);
        when(redissonClient.getMultiLock(any(RLock[].class))).thenReturn(multiLock);
        when(multiLock.tryLock(3, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(multiLock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("done");

        Object result = aspect.executeWithLock(joinPoint);

        assertEquals("done", result);
        InOrder inOrder = inOrder(redissonClient);
        inOrder.verify(redissonClient).getLock("lock:a");
        inOrder.verify(redissonClient).getLock("lock:b");
        inOrder.verify(redissonClient).getLock("lock:c");
        verify(redissonClient).getMultiLock(any(RLock[].class));
        verify(multiLock).unlock();
    }

    @Test
    void 단일_락_키는_MultiLock을_만들지_않고_기존처럼_실행한다() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("executeWithSingleLock");
        RLock lock = mock(RLock.class);

        when(redissonClient.getLock("lock:single")).thenReturn(lock);
        when(lock.tryLock(3, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("done");

        Object result = aspect.executeWithLock(joinPoint);

        assertEquals("done", result);
        verify(redissonClient, never()).getMultiLock(any(RLock[].class));
        verify(lock).unlock();
    }

    private ProceedingJoinPoint joinPoint(String methodName) throws NoSuchMethodException {
        TestTarget target = new TestTarget();
        Method method = TestTarget.class.getDeclaredMethod(methodName);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(signature.getMethod()).thenReturn(method);
        return joinPoint;
    }

    private static class TestTarget {

        @DistributedLock(keys = {"'lock:c'", "'lock:a'", "'lock:b'", "'lock:a'"})
        public void executeWithMultipleLocks() {
        }

        @DistributedLock(keys = "'lock:single'")
        public void executeWithSingleLock() {
        }
    }
}
