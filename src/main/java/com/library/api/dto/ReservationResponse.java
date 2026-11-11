package com.library.api.dto;

import com.library.api.entity.Reservation;
import com.library.api.entity.ReservationStatus;
import java.time.LocalDate;

public record ReservationResponse(
        Long id,
        Long bookId,
        Long memberId,
        LocalDate reservationDate,
        ReservationStatus status,
        LocalDate cancelledDate) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getBook().getId(),
                reservation.getMember().getId(), reservation.getReservationDate(),
                reservation.getStatus(), reservation.getCancelledDate());
    }
}
