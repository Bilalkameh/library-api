package com.library.api.service;

import com.library.api.entity.Book;
import com.library.api.entity.Borrowing;
import com.library.api.entity.Member;
import com.library.api.repository.BookRepository;
import com.library.api.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
