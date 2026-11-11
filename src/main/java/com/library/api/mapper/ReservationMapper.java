package com.library.api.mapper;

import com.library.api.dto.ReservationResponse;
import com.library.api.entity.Reservation;
import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {
    public ReservationResponse toDTO(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getBook().getId(),
                reservation.getMember().getId(), reservation.getReservationDate(),
                reservation.getStatus(), reservation.getCancelledDate());
    }
}
