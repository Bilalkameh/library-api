package com.library.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request to borrow an available book. Dates and fines are calculated by the server.")
public record BorrowingRequest(
        @NotNull @Positive @Schema(description = "Existing available book ID", example = "6") Long bookId,
        @NotNull @Positive @Schema(description = "Existing member ID", example = "4") Long memberId) {
}
