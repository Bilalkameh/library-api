package com.library.api.service;

import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class FineCalculationTest {
    private final BookRepository books = mock(BookRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final BorrowingRepository borrowings = mock(BorrowingRepository.class);
    private final Clock clock = mock(Clock.class);
    private final BorrowingService service = new BorrowingService(books, members, borrowings, clock);

    @ParameterizedTest(name = "{0} days after the due date gives a fine of EUR {1}")
    @CsvSource({
            "-1, 0.00",
            "0, 0.00",
            "1, 0.50",
            "7, 3.50"
    })
    void calculateFine_shouldChargeOnlyOverdueDays(long daysAfterDueDate, String expectedFine) {
        LocalDate dueDate = LocalDate.of(2026, 11, 11);

        BigDecimal fine = service.calculateFine(dueDate, dueDate.plusDays(daysAfterDueDate));

        // BigDecimal equality verifies both the monetary value and the required scale of two.
        assertEquals(new BigDecimal(expectedFine), fine);
        verifyNoInteractions(books, members, borrowings, clock);
    }
}
