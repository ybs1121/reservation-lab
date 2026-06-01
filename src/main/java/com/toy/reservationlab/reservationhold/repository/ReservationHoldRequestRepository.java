package com.toy.reservationlab.reservationhold.repository;

import com.toy.reservationlab.reservationhold.entity.ReservationHoldRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationHoldRequestRepository extends JpaRepository<ReservationHoldRequest, String> {
}
