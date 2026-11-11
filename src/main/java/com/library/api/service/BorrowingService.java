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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BorrowingService {
    private static final BigDecimal DAILY_FINE_RATE = new BigDecimal("0.50");
    private static final int DEFAULT_LOAN_DAYS = 14;

    private final BookRepository books;
    private final MemberRepository members;
    private final BorrowingRepository borrowings;
    private final Clock clock;

    public BorrowingService(BookRepository books, MemberRepository members,
                            BorrowingRepository borrowings, Clock clock) {
        this.books = books;
        this.members = members;
        this.borrowings = borrowings;
        this.clock = clock;
    }

    @Transactional
    public Borrowing borrowBook(Long bookId, Long memberId) {
        Book book = books.findByIdForUpdate(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId));
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
        if (!book.isAvailable()) {
            throw new IllegalStateException("Book is already borrowed.");
        }

        LocalDate today = LocalDate.now(clock);
        Borrowing borrowing = new Borrowing();
        borrowing.setBook(book);
        borrowing.setMember(member);
        borrowing.setBorrowDate(today);
        borrowing.setDueDate(today.plusDays(DEFAULT_LOAN_DAYS));
        book.setAvailable(false);
        return borrowings.save(borrowing);
    }

    @Transactional
    public Borrowing returnBook(Long borrowingId) {
        Borrowing borrowing = borrowings.findByIdForUpdate(borrowingId)
                .orElseThrow(() -> new ResourceNotFoundException("Borrowing", borrowingId));
        if (borrowing.getReturnDate() != null) {
            throw new IllegalStateException("Borrowing has already been returned.");
        }

        Long bookId = borrowing.getBook().getId();
        Book book = books.findByIdForUpdate(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId));
        applyReturn(borrowing, book);
        return borrowings.save(borrowing);
    }

    @Transactional(readOnly = true)
    public List<Borrowing> getActiveBorrowings(Long memberId) {
        if (!members.existsById(memberId)) {
            throw new ResourceNotFoundException("Member", memberId);
        }
        return borrowings.findByMemberIdAndReturnDateIsNullOrderByIdAsc(memberId);
    }

    public BigDecimal calculateFine(LocalDate dueDate, LocalDate returnDate) {
        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, returnDate));
        return DAILY_FINE_RATE.multiply(BigDecimal.valueOf(overdueDays));
    }

    private void applyReturn(Borrowing borrowing, Book book) {
        LocalDate today = LocalDate.now(clock);
        borrowing.setReturnDate(today);
        borrowing.setFineAmount(calculateFine(borrowing.getDueDate(), today));
        book.setAvailable(true);
    }
}
