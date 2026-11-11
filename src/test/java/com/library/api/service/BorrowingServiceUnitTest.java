package com.library.api.service;

import com.library.api.entity.Book;
import com.library.api.entity.Borrowing;
import com.library.api.entity.Member;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BorrowingServiceUnitTest {
    private static final long BOOK_ID = 11L;
    private static final long MEMBER_ID = 21L;
    private static final long BORROWING_ID = 31L;
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 29);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-02-28T22:30:00Z"), ZoneId.of("Asia/Beirut"));

    @Mock private BookRepository books;
    @Mock private MemberRepository members;
    @Mock private BorrowingRepository borrowings;

    private BorrowingService service;
    private Book book;
    private Member member;

    @BeforeEach
    void setUp() {
        service = new BorrowingService(books, members, borrowings, CLOCK);
        book = new Book();
        book.setId(BOOK_ID);
        book.setAvailable(true);
        member = new Member();
        member.setId(MEMBER_ID);
    }

    @Test
    void borrowAvailableBookCreatesLoanAndMarksBookUnavailable() {
        when(books.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book));
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(borrowings.save(any(Borrowing.class))).thenAnswer(invocation -> {
            Borrowing saved = invocation.getArgument(0);
            saved.setId(BORROWING_ID);
            return saved;
        });

        Borrowing result = service.borrowBook(BOOK_ID, MEMBER_ID);

        assertEquals(BORROWING_ID, result.getId());
        assertSame(book, result.getBook());
        assertSame(member, result.getMember());
        assertEquals(TODAY, result.getBorrowDate());
        assertEquals(LocalDate.of(2024, 3, 14), result.getDueDate());
        assertNull(result.getReturnDate());
        assertEquals(new BigDecimal("0.00"), result.getFineAmount());
        assertFalse(book.isAvailable());
        verify(borrowings).save(result);
    }

    @Test
    void borrowMissingBookRejectsBeforeMemberLookupOrSave() {
        when(books.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class,
                () -> service.borrowBook(BOOK_ID, MEMBER_ID));

        assertEquals("Book with id 11 was not found", error.getMessage());
        verifyNoInteractions(members, borrowings);
    }

    @Test
    void borrowMissingMemberLeavesBookAvailableAndDoesNotSave() {
        when(books.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book));
        when(members.findById(MEMBER_ID)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class,
                () -> service.borrowBook(BOOK_ID, MEMBER_ID));

        assertEquals("Member with id 21 was not found", error.getMessage());
        assertTrue(book.isAvailable());
        verifyNoInteractions(borrowings);
    }

    @Test
    void borrowUnavailableBookRejectsWithoutSaving() {
        book.setAvailable(false);
        when(books.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book));
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.borrowBook(BOOK_ID, MEMBER_ID));

        assertEquals("Book is already borrowed.", error.getMessage());
        assertFalse(book.isAvailable());
        verifyNoInteractions(borrowings);
    }

    @Test
    void returnBeforeDueDateRestoresAvailabilityWithZeroFine() {
        assertSuccessfulReturn(TODAY.plusDays(1), "0.00");
    }

    @Test
    void returnThreeDaysLateChargesOneEuroFifty() {
        assertSuccessfulReturn(TODAY.minusDays(3), "1.50");
    }

    @Test
    void returnMissingBorrowingRejectsWithoutBookLookupOrSave() {
        when(borrowings.findByIdForUpdate(BORROWING_ID)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class,
                () -> service.returnBook(BORROWING_ID));

        assertEquals("Borrowing with id 31 was not found", error.getMessage());
        verifyNoInteractions(books, members);
        verify(borrowings, never()).save(any());
    }

    @Test
    void returnExactlyOnDueDateRestoresAvailabilityWithZeroFine() {
        assertSuccessfulReturn(TODAY, "0.00");
    }

    @Test
    void returnAlreadyReturnedLoanPreservesSettlementWithoutSaving() {
        Borrowing original = activeLoan(TODAY.minusDays(3));
        LocalDate firstReturnDate = TODAY.minusDays(1);
        original.setReturnDate(firstReturnDate);
        original.setFineAmount(new BigDecimal("1.00"));
        // The book may already be unavailable again for a later loan.
        when(borrowings.findByIdForUpdate(BORROWING_ID)).thenReturn(Optional.of(original));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.returnBook(BORROWING_ID));

        assertEquals("Borrowing has already been returned.", error.getMessage());
        assertEquals(BORROWING_ID, original.getId());
        assertEquals(firstReturnDate, original.getReturnDate());
        assertEquals(new BigDecimal("1.00"), original.getFineAmount());
        assertEquals(TODAY.minusDays(17), original.getBorrowDate());
        assertEquals(TODAY.minusDays(3), original.getDueDate());
        assertSame(book, original.getBook());
        assertSame(member, original.getMember());
        assertFalse(book.isAvailable());
        verify(borrowings, never()).save(any());
        verifyNoInteractions(books, members);
    }

    @Test
    void returnMissingBookLeavesLoanUnsettledWithoutSaving() {
        Borrowing original = activeLoan(TODAY.minusDays(3));
        LocalDate originalBorrowDate = original.getBorrowDate();
        when(borrowings.findByIdForUpdate(BORROWING_ID)).thenReturn(Optional.of(original));
        when(books.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class,
                () -> service.returnBook(BORROWING_ID));

        assertEquals("Book with id 11 was not found", error.getMessage());
        assertEquals(BORROWING_ID, original.getId());
        assertNull(original.getReturnDate());
        assertEquals(new BigDecimal("0.00"), original.getFineAmount());
        assertEquals(originalBorrowDate, original.getBorrowDate());
        assertEquals(TODAY.minusDays(3), original.getDueDate());
        assertSame(book, original.getBook());
        assertSame(member, original.getMember());
        assertFalse(book.isAvailable());
        verify(borrowings, never()).save(any());
        verifyNoInteractions(members);
    }

    @Test
    void fineOnDueDateIsZero() {
        assertFine(TODAY, "0.00");
    }

    @Test
    void fineOneDayLateIsFiftyCents() {
        assertFine(TODAY.plusDays(1), "0.50");
    }

    @Test
    void fineSevenDaysLateIsThreeEurosFifty() {
        assertFine(TODAY.plusDays(7), "3.50");
    }

    @Test
    void fineBeforeDueDateIsZero() {
        assertFine(TODAY.minusDays(1), "0.00");
    }

    @Test
    void activeBorrowingsForExistingMemberReturnsRepositoryResults() {
        Borrowing first = activeLoan(TODAY.plusDays(1));
        Borrowing second = activeLoan(TODAY.plusDays(2));
        second.setId(32L);
        when(members.existsById(MEMBER_ID)).thenReturn(true);
        when(borrowings.findByMemberIdAndReturnDateIsNullOrderByIdAsc(MEMBER_ID))
                .thenReturn(List.of(first, second));

        List<Borrowing> result = service.getActiveBorrowings(MEMBER_ID);

        assertEquals(List.of(BORROWING_ID, 32L), result.stream().map(Borrowing::getId).toList());
        verify(borrowings).findByMemberIdAndReturnDateIsNullOrderByIdAsc(MEMBER_ID);
        verifyNoInteractions(books);
    }

    @Test
    void activeBorrowingsForExistingMemberCanBeEmpty() {
        when(members.existsById(MEMBER_ID)).thenReturn(true);
        when(borrowings.findByMemberIdAndReturnDateIsNullOrderByIdAsc(MEMBER_ID)).thenReturn(List.of());

        assertTrue(service.getActiveBorrowings(MEMBER_ID).isEmpty());
        verify(borrowings).findByMemberIdAndReturnDateIsNullOrderByIdAsc(MEMBER_ID);
        verifyNoInteractions(books);
    }

    @Test
    void activeBorrowingsRejectsMissingMemberBeforeQuery() {
        when(members.existsById(MEMBER_ID)).thenReturn(false);

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class,
                () -> service.getActiveBorrowings(MEMBER_ID));

        assertEquals("Member with id 21 was not found", error.getMessage());
        verifyNoInteractions(books, borrowings);
    }

    private void assertSuccessfulReturn(LocalDate dueDate, String expectedFine) {
        Borrowing original = activeLoan(dueDate);
        LocalDate originalBorrowDate = original.getBorrowDate();
        when(borrowings.findByIdForUpdate(BORROWING_ID)).thenReturn(Optional.of(original));
        when(books.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book));
        when(borrowings.save(original)).thenReturn(original);

        Borrowing result = service.returnBook(BORROWING_ID);

        assertSame(original, result);
        assertEquals(TODAY, result.getReturnDate());
        assertEquals(new BigDecimal(expectedFine), result.getFineAmount());
        assertEquals(originalBorrowDate, result.getBorrowDate());
        assertEquals(dueDate, result.getDueDate());
        assertSame(book, result.getBook());
        assertSame(member, result.getMember());
        assertTrue(book.isAvailable());
        verify(borrowings).save(original);
        verifyNoInteractions(members);
    }

    private Borrowing activeLoan(LocalDate dueDate) {
        book.setAvailable(false);
        Borrowing borrowing = new Borrowing();
        borrowing.setId(BORROWING_ID);
        borrowing.setBook(book);
        borrowing.setMember(member);
        borrowing.setBorrowDate(dueDate.minusDays(14));
        borrowing.setDueDate(dueDate);
        return borrowing;
    }

    private void assertFine(LocalDate returnDate, String expectedFine) {
        assertEquals(new BigDecimal(expectedFine), service.calculateFine(TODAY, returnDate));
        verifyNoInteractions(books, members, borrowings);
    }
}
