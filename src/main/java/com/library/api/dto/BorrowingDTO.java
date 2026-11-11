package com.library.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Borrowing result with flat related-resource IDs and server-controlled dates and fine.")
public record BorrowingDTO(
        @Schema(description = "Generated borrowing ID", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
        Long id,
        @Schema(description = "Borrowed book ID", example = "6") Long bookId,
        @Schema(description = "Borrowing member ID", example = "4") Long memberId,
        @Schema(description = "Local date the loan started", example = "2026-11-11", accessMode = Schema.AccessMode.READ_ONLY)
        LocalDate borrowDate,
        @Schema(description = "Due date, 14 days after borrowing", example = "2026-11-25", accessMode = Schema.AccessMode.READ_ONLY)
        LocalDate dueDate,
        @Schema(description = "Return date; null until returned", example = "2026-11-28", nullable = true,
                accessMode = Schema.AccessMode.READ_ONLY) LocalDate returnDate,
        @Schema(description = "Fine in EUR; 0.50 per overdue day, initially 0.00", example = "1.50",
                accessMode = Schema.AccessMode.READ_ONLY) BigDecimal fineAmount) {
}
