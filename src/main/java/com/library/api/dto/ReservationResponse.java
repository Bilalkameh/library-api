package com.library.api.dto;

import com.library.api.entity.Reservation;
import com.library.api.entity.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Reservation history entry; returning a book does not automatically change its status.")
public record ReservationResponse(
        @Schema(description = "Generated reservation ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY) Long id,
        @Schema(description = "Reserved book ID", example = "6") Long bookId,
        @Schema(description = "Reserving member ID", example = "4") Long memberId,
        @Schema(description = "Original local creation date", example = "2026-11-11", accessMode = Schema.AccessMode.READ_ONLY)
        LocalDate reservationDate,
        @Schema(description = "Current reservation status", example = "ACTIVE", accessMode = Schema.AccessMode.READ_ONLY)
        ReservationStatus status,
        @Schema(description = "First cancellation date; null while active", example = "2026-11-12", nullable = true,
                accessMode = Schema.AccessMode.READ_ONLY) LocalDate cancelledDate) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getBook().getId(),
                reservation.getMember().getId(), reservation.getReservationDate(),
                reservation.getStatus(), reservation.getCancelledDate());
    }
}
