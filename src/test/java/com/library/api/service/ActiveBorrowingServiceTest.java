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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:activeborrowingtest;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@Transactional
class ActiveBorrowingServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @Autowired private BorrowingService service;
    @Autowired private BookRepository books;
    @Autowired private BorrowingRepository borrowings;
    @Autowired private MemberRepository members;
    @Autowired private EntityManager entityManager;

    private Member targetMember;
    private Member otherMember;

    @BeforeEach
    void setUp() {
        targetMember = saveMember("Query target member");
        otherMember = saveMember("Query other member");
    }

    @Test
    void getActiveBorrowings_shouldFilterByMemberAndReturnDateInAscendingIdOrder() {
        Borrowing firstActive = saveBorrowing(targetMember, false);
        saveBorrowing(otherMember, false);
        saveBorrowing(targetMember, true);
        Borrowing secondActive = saveBorrowing(targetMember, false);
        flushAndClear();

        List<Borrowing> result = service.getActiveBorrowings(targetMember.getId());

        assertEquals(List.of(firstActive.getId(), secondActive.getId()),
                result.stream().map(Borrowing::getId).toList());
        assertTrue(result.stream().allMatch(borrowing -> borrowing.getReturnDate() == null));
        assertTrue(result.stream().allMatch(borrowing ->
                targetMember.getId().equals(borrowing.getMember().getId())));
    }

    @Test
    void getActiveBorrowings_withNoActiveLoans_shouldReturnEmptyList() {
        saveBorrowing(targetMember, true);
        saveBorrowing(otherMember, false);
        flushAndClear();

        assertTrue(service.getActiveBorrowings(targetMember.getId()).isEmpty());
    }

    @Test
    void getActiveBorrowings_withMissingMember_shouldThrowNotFound() {
        flushAndClear();
        assertFalse(members.existsById(Long.MAX_VALUE));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getActiveBorrowings(Long.MAX_VALUE));
    }

    private Member saveMember(String name) {
        Member member = new Member();
        member.setName(name);
        member.setEmail(UUID.randomUUID() + "@example.com");
        member.setMembershipDate(TODAY.minusMonths(1));
        member.setActive(true);
        return members.save(member);
    }

    private Borrowing saveBorrowing(Member member, boolean returned) {
        Book book = new Book();
        book.setIsbn("active-" + UUID.randomUUID().toString().substring(0, 20));
        book.setTitle("Active borrowing fixture");
        book.setAuthor("Test Author");
        book.setGenre("Engineering");
        book.setPublishedYear(2026);
        book.setAvailable(returned);
        book = books.save(book);

        Borrowing borrowing = new Borrowing();
        borrowing.setBook(book);
        borrowing.setMember(member);
        borrowing.setBorrowDate(TODAY.minusDays(7));
        borrowing.setDueDate(TODAY.plusDays(7));
        if (returned) {
            borrowing.setReturnDate(TODAY.minusDays(1));
        }
        return borrowings.save(borrowing);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
