package com.library.api.controller;

import com.library.api.dto.ApiError;
import com.library.api.dto.ReservationRequest;
import com.library.api.dto.ReservationResponse;
import com.library.api.mapper.ReservationMapper;
import com.library.api.service.ReservationService;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Reservations", description = "Interest in unavailable books and retained cancellation history")
@ApiResponse(responseCode = "500", description = "Unexpected server error",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
public class ReservationController {
    private final ReservationService reservationService;
    private final ReservationMapper mapper;

    public ReservationController(ReservationService reservationService, ReservationMapper mapper) {
        this.reservationService = reservationService;
        this.mapper = mapper;
    }

    @PostMapping("/reservations")
    @Operation(summary = "Reserve an unavailable book", description = "Any existing member is eligible. One active reservation per book/member pair is allowed; client-supplied status and dates are ignored. No borrowing priority is granted.")
    @ApiResponse(responseCode = "201", description = "Created active reservation")
    @ApiResponse(responseCode = "400", description = "Invalid book/member IDs or malformed JSON",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Book or member not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Book is available for direct borrowing, or an active pair already exists",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Request media type is not supported",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDTO(
                reservationService.reserveBook(request.bookId(), request.memberId())));
    }

    @GetMapping("/members/{memberId}/reservations")
    @Operation(summary = "List a member's complete reservation history", description = "Includes ACTIVE and CANCELLED records in ascending reservation ID order.")
    @ApiResponse(responseCode = "200", description = "Reservation history, including an empty list")
    @ApiResponse(responseCode = "400", description = "Malformed member ID",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Member not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public List<ReservationResponse> memberHistory(@PathVariable Long memberId) {
        return reservationService.getReservationsForMember(memberId).stream()
                .map(mapper::toDTO).toList();
    }

    @PutMapping("/reservations/{id}/cancel")
    @Operation(summary = "Cancel an active reservation", description = "Retains its identity, original date, and history, and records cancellation once. Book availability is unchanged.")
    @ApiResponse(responseCode = "200", description = "Retained cancelled reservation")
    @ApiResponse(responseCode = "400", description = "Malformed reservation ID",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Reservation not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Reservation has already been cancelled",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public ReservationResponse cancel(@PathVariable Long id) {
        return mapper.toDTO(reservationService.cancelReservation(id));
    }
}
