package com.toy.reservationlab.common.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    String key();

    long waitTime() default 3;

    long leaseTime() default 5;

    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
