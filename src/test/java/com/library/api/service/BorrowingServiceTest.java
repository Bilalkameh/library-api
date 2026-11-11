package com.library.api.service;

import com.library.api.entity.Book;
import com.library.api.entity.Borrowing;
import com.library.api.entity.Member;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:borrowingtest;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@Transactional
class BorrowingServiceTest {
    @Autowired
    private BorrowingService borrowingService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BorrowingRepository borrowingRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void borrowBook_shouldMarkBookAsUnavailable() {
        Book book = new Book();
        book.setIsbn("978-0-13-468599-1");
        book.setTitle("Clean Code");
        book.setAuthor("Robert C. Martin");
        book.setGenre("Engineering");
        book.setPublishedYear(2008);
        book.setAvailable(true);
        book = bookRepository.saveAndFlush(book);

        Member member = new Member();
        member.setName("Alice");
        member.setEmail("alice.lab2@example.com");
        member.setMembershipDate(LocalDate.now());
        member.setActive(true);
        member = memberRepository.saveAndFlush(member);

        LocalDate earliestBorrowDate = LocalDate.now();
        Borrowing result = borrowingService.borrowBook(book.getId(), member.getId());
        LocalDate latestBorrowDate = LocalDate.now();
        assertNotNull(result);
        assertNotNull(result.getId());

        entityManager.flush();
        entityManager.clear();

        assertFalse(bookRepository.findById(book.getId()).orElseThrow().isAvailable());
        Borrowing persisted = entityManager.find(Borrowing.class, result.getId());
        assertNotNull(persisted);
        assertNotNull(persisted.getBorrowDate());
        assertFalse(persisted.getBorrowDate().isBefore(earliestBorrowDate));
        assertFalse(persisted.getBorrowDate().isAfter(latestBorrowDate));
        assertEquals(persisted.getBorrowDate().plusDays(14), persisted.getDueDate());
        assertEquals(persisted.getDueDate(), result.getDueDate());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void borrowBook_withMissingBook_shouldNotCreateBorrowing() {
        Member member = saveMember("missing-book.lab2@example.com");
        try {
            assertFalse(bookRepository.existsById(Long.MAX_VALUE));
            long borrowingCountBefore = borrowingRepository.count();

            assertThrows(ResourceNotFoundException.class,
                    () -> borrowingService.borrowBook(Long.MAX_VALUE, member.getId()));

            assertEquals(borrowingCountBefore, borrowingRepository.count());
            assertTrue(memberRepository.existsById(member.getId()));
        } finally {
            removeFixtures(null, member);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void borrowBook_withMissingMember_shouldNotCommitBookChangeOrBorrowing() {
        Book book = saveBook("service-missing-member", true);
        try {
            assertFalse(memberRepository.existsById(Long.MAX_VALUE));
            long borrowingCountBefore = borrowingRepository.count();

            assertThrows(ResourceNotFoundException.class,
                    () -> borrowingService.borrowBook(book.getId(), Long.MAX_VALUE));

            assertEquals(borrowingCountBefore, borrowingRepository.count());
            assertTrue(bookRepository.findById(book.getId()).orElseThrow().isAvailable());
        } finally {
            removeFixtures(book, null);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void borrowBook_withUnavailableBook_shouldNotCommitBookChangeOrBorrowing() {
        Book book = saveBook("service-unavailable", false);
        Member member = saveMember("unavailable-book.lab2@example.com");
        try {
            long borrowingCountBefore = borrowingRepository.count();

            assertThrows(IllegalStateException.class,
                    () -> borrowingService.borrowBook(book.getId(), member.getId()));

            assertEquals(borrowingCountBefore, borrowingRepository.count());
            assertFalse(bookRepository.findById(book.getId()).orElseThrow().isAvailable());
        } finally {
            removeFixtures(book, member);
        }
    }

    private Book saveBook(String isbn, boolean available) {
        Book book = new Book();
        book.setIsbn(isbn);
        book.setTitle("Service borrowing fixture");
        book.setAuthor("Test Author");
        book.setGenre("Engineering");
        book.setPublishedYear(2026);
        book.setAvailable(available);
        return bookRepository.saveAndFlush(book);
    }

    private Member saveMember(String email) {
        Member member = new Member();
        member.setName("Service test member");
        member.setEmail(email);
        member.setMembershipDate(LocalDate.now());
        member.setActive(true);
        return memberRepository.saveAndFlush(member);
    }

    private void removeFixtures(Book book, Member member) {
        // Failure tests have no outer transaction: verify committed state first, then clean up.
        // Remove only records tied to these fixtures, including an incorrectly persisted loan.
        for (Borrowing borrowing : borrowingRepository.findAll()) {
            if ((book != null && book.getId().equals(borrowing.getBook().getId()))
                    || (member != null && member.getId().equals(borrowing.getMember().getId()))) {
                borrowingRepository.deleteById(borrowing.getId());
            }
        }
        if (book != null) {
            bookRepository.deleteById(book.getId());
        }
        if (member != null) {
            memberRepository.deleteById(member.getId());
        }
    }
}
