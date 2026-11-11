package com.library.api.controller;

import com.library.api.dto.ReservationRequest;
import com.library.api.dto.ReservationResponse;
import com.library.api.service.ReservationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.from(
                reservationService.reserveBook(request.bookId(), request.memberId())));
    }

    @GetMapping("/members/{memberId}/reservations")
    public List<ReservationResponse> memberHistory(@PathVariable Long memberId) {
        return reservationService.getReservationsForMember(memberId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @PutMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id) {
        return ReservationResponse.from(reservationService.cancelReservation(id));
    }
}
