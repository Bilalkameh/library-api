package com.library.api.service;

import com.library.api.entity.Book;
import com.library.api.entity.Borrowing;
import com.library.api.entity.Member;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import com.library.api.support.FixedClockConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:returntest;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@Import(FixedClockConfiguration.class)
class ReturnServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 29);

    @Autowired private BorrowingService service;
    @Autowired private BookRepository books;
    @Autowired private MemberRepository members;
    @Autowired private BorrowingRepository borrowings;

    private Book book;
    private Member member;

    @BeforeEach
    void setUp() {
        book = new Book();
        book.setIsbn("return-" + UUID.randomUUID().toString().substring(0, 20));
        book.setTitle("Return service fixture");
        book.setAuthor("Test Author");
        book.setGenre("Engineering");
        book.setPublishedYear(2026);
        book.setAvailable(false);
        book = books.saveAndFlush(book);

        member = new Member();
        member.setName("Return test member");
        member.setEmail(UUID.randomUUID() + "@example.com");
        member.setMembershipDate(TODAY.minusMonths(1));
        member.setActive(true);
        member = members.saveAndFlush(member);
    }

    @AfterEach
    void tearDown() {
        // Service calls own their transactions; clean fixtures only after fresh-read assertions.
        for (Borrowing borrowing : borrowings.findAll()) {
            if (book != null && book.getId().equals(borrowing.getBook().getId())) {
                borrowings.deleteById(borrowing.getId());
            }
        }
        if (book != null && book.getId() != null) {
            books.deleteById(book.getId());
        }
        if (member != null && member.getId() != null) {
            members.deleteById(member.getId());
        }
    }

    @ParameterizedTest(name = "due date offset {0} days produces a return fine of EUR {1}")
    @CsvSource({
            "1, 0.00",
            "0, 0.00",
            "-3, 1.50"
    })
    void returnBook_shouldPersistLocalDateFineAndAvailability(int dueDateOffset, String expectedFine) {
        Borrowing original = saveBorrowing(TODAY.plusDays(dueDateOffset));
        long countBeforeReturn = borrowings.count();

        Borrowing result = service.returnBook(original.getId());

        assertNotNull(result);
        assertEquals(original.getId(), result.getId());
        assertEquals(TODAY, result.getReturnDate());
        assertEquals(new BigDecimal(expectedFine), result.getFineAmount());

        Borrowing persisted = borrowings.findById(original.getId()).orElseThrow();
        assertEquals(TODAY, persisted.getReturnDate());
        assertEquals(new BigDecimal(expectedFine), persisted.getFineAmount());
        assertEquals(original.getBorrowDate(), persisted.getBorrowDate());
        assertEquals(original.getDueDate(), persisted.getDueDate());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
        assertEquals(countBeforeReturn, borrowings.count());
    }

    @Test
    void returnBook_withMissingBorrowing_shouldNotChangeStoredState() {
        long countBeforeReturn = borrowings.count();
        assertFalse(borrowings.existsById(Long.MAX_VALUE));

        assertThrows(ResourceNotFoundException.class, () -> service.returnBook(Long.MAX_VALUE));

        assertEquals(countBeforeReturn, borrowings.count());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void returnBook_whenAlreadyReturned_shouldPreserveFirstReturnDateAndFine() {
        Borrowing original = saveBorrowing(TODAY.minusDays(3));
        LocalDate firstReturnDate = TODAY.minusDays(1);
        BigDecimal firstFine = new BigDecimal("1.00");
        original.setReturnDate(firstReturnDate);
        original.setFineAmount(firstFine);
        borrowings.saveAndFlush(original);
        book.setAvailable(true);
        books.saveAndFlush(book);
        long countBeforeReturn = borrowings.count();

        assertThrows(IllegalStateException.class, () -> service.returnBook(original.getId()));

        Borrowing persisted = borrowings.findById(original.getId()).orElseThrow();
        assertEquals(firstReturnDate, persisted.getReturnDate());
        assertEquals(firstFine, persisted.getFineAmount());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
        assertEquals(countBeforeReturn, borrowings.count());
    }

    private Borrowing saveBorrowing(LocalDate dueDate) {
        Borrowing borrowing = new Borrowing();
        borrowing.setBook(book);
        borrowing.setMember(member);
        borrowing.setBorrowDate(dueDate.minusDays(14));
        borrowing.setDueDate(dueDate);
        return borrowings.saveAndFlush(borrowing);
    }
}
