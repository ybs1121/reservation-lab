package com.toy.reservationlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReservationLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationLabApplication.class, args);
    }

}
