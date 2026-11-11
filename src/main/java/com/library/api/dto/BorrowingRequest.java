package com.library.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BorrowingRequest(
        @NotNull @Positive Long bookId,
        @NotNull @Positive Long memberId) {
}
