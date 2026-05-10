package com.toy.reservationlab.user.service;

import static com.toy.reservationlab.common.component.ErrorCode.DUPLICATE_PHONE;
import static com.toy.reservationlab.common.component.ErrorCode.USER_NOT_FOUND;

import com.toy.reservationlab.common.component.BizException;
import com.toy.reservationlab.reservation.entity.Reservation;
import com.toy.reservationlab.reservation.entity.ReservationStatus;
import com.toy.reservationlab.reservation.repository.ReservationRepository;
import com.toy.reservationlab.reservationslot.repository.ReservationSlotRepository;
import com.toy.reservationlab.user.entity.User;
import com.toy.reservationlab.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String NOT_DELETED = "N";
    private static final List<ReservationStatus> ACTIVE_PARTY_SIZE_STATUSES = List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.NO_SHOW
    );

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository reservationSlotRepository;

    @Transactional
    public User createUser(String userId, String name, String phone, String createdBy) {
        validatePhoneNotExists(phone);

        User user = User.create(userId, name, phone, createdBy);
        return userRepository.save(user);
    }

    public User getUser(String userId) {
        return findUser(userId);
    }

    @Transactional
    public User updateUser(String userId, String name, String phone, String updatedBy) {
        User user = findUser(userId);
        validatePhoneNotExists(phone, userId);

        user.update(name, phone, updatedBy);
        return user;
    }

    @Transactional
    public User deleteUser(String userId, String updatedBy) {
        User user = findUser(userId);
        user.markDeleted(updatedBy);
        cancelConfirmedReservations(userId, updatedBy);
        return user;
    }

    private void validatePhoneNotExists(String phone) {
        if (userRepository.existsByPhone(phone)) {
            throw new BizException(DUPLICATE_PHONE);
        }
    }

    private void validatePhoneNotExists(String phone, String userId) {
        if (userRepository.countByPhoneAndUserIdNot(phone, userId) > 0) {
            throw new BizException(DUPLICATE_PHONE);
        }
    }

    private void cancelConfirmedReservations(String userId, String updatedBy) {
        for (Reservation reservation : reservationRepository.findByUserIdAndStatusAndDelYn(
                userId,
                ReservationStatus.CONFIRMED,
                NOT_DELETED
        )) {
            long activePartySize = reservationRepository.sumPartySizeBySlotIdAndStatuses(
                    reservation.getSlotId(),
                    ACTIVE_PARTY_SIZE_STATUSES
            );
            reservation.cancel(updatedBy);
            reservationSlotRepository.findById(reservation.getSlotId())
                    .ifPresent(slot -> slot.restoreAvailableIfNotFull(
                            (int) activePartySize - reservation.getPartySize(),
                            updatedBy
                    ));
        }
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(USER_NOT_FOUND));
    }
}
