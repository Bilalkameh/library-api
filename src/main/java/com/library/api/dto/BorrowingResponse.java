package com.library.api.dto;

import com.library.api.entity.Borrowing;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BorrowingResponse(
        Long id,
        Long bookId,
        Long memberId,
        LocalDate borrowDate,
        LocalDate dueDate,
        LocalDate returnDate,
        BigDecimal fineAmount) {

    public static BorrowingResponse from(Borrowing borrowing) {
        return new BorrowingResponse(borrowing.getId(), borrowing.getBook().getId(),
                borrowing.getMember().getId(), borrowing.getBorrowDate(), borrowing.getDueDate(),
                borrowing.getReturnDate(), borrowing.getFineAmount());
    }
}
