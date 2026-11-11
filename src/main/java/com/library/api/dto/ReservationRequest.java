package com.library.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request for an unavailable book; extra properties cannot override server-controlled state.")
public record ReservationRequest(
        @NotNull @Positive @Schema(description = "Existing unavailable book ID", example = "6") Long bookId,
        @NotNull @Positive @Schema(description = "Existing member ID, including inactive members", example = "4") Long memberId) {
}
