package com.library.api.controller;

import com.library.api.dto.ApiError;
import com.library.api.dto.BorrowingRequest;
import com.library.api.dto.BorrowingDTO;
import com.library.api.mapper.BorrowingMapper;
import com.library.api.service.BorrowingService;
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
@Tag(name = "Borrowings", description = "Loans, returns, overdue fines, and active member loans")
@ApiResponse(responseCode = "500", description = "Unexpected server error",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
public class BorrowingController {
    private final BorrowingService borrowingService;
    private final BorrowingMapper mapper;

    public BorrowingController(BorrowingService borrowingService, BorrowingMapper mapper) {
        this.borrowingService = borrowingService;
        this.mapper = mapper;
    }

    @PostMapping("/borrowings")
    @Operation(summary = "Borrow an available book", description = "Starts a 14-day loan and makes the book unavailable. Dates and fines are server-controlled.")
    @ApiResponse(responseCode = "201", description = "Created borrowing")
    @ApiResponse(responseCode = "400", description = "Invalid book/member IDs or malformed JSON",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Book or member not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Book is already borrowed",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Request media type is not supported",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<BorrowingDTO> borrow(@Valid @RequestBody BorrowingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDTO(
                borrowingService.borrowBook(request.bookId(), request.memberId())));
    }

    @PutMapping("/borrowings/{id}/return")
    @Operation(summary = "Return a borrowed book", description = "Records the local return date, restores availability, and charges EUR 0.50 per overdue day. Existing reservations remain unchanged.")
    @ApiResponse(responseCode = "200", description = "Returned borrowing with calculated fine")
    @ApiResponse(responseCode = "400", description = "Malformed borrowing ID",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Borrowing not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Borrowing has already been returned",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public BorrowingDTO returnBook(@PathVariable Long id) {
        return mapper.toDTO(borrowingService.returnBook(id));
    }

    @GetMapping("/members/{id}/borrowings")
    @Operation(summary = "List a member's active borrowings", description = "Returns unreturned loans in ascending borrowing ID order. Returned loans are excluded.")
    @ApiResponse(responseCode = "200", description = "Active borrowings, including an empty list")
    @ApiResponse(responseCode = "400", description = "Malformed member ID",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Member not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public List<BorrowingDTO> activeBorrowings(@PathVariable Long id) {
        return borrowingService.getActiveBorrowings(id).stream().map(mapper::toDTO).toList();
    }
}
