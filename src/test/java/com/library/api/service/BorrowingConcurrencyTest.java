package com.library.api.service;

import com.library.api.entity.Book;
import com.library.api.entity.Borrowing;
import com.library.api.entity.Member;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import com.library.api.support.FixedClockConfiguration;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:borrowingconcurrencytest;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.h2.console.enabled=false"
})
@Import(FixedClockConfiguration.class)
class BorrowingConcurrencyTest {
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 29);
    private static final int WAIT_SECONDS = 15;

    @Autowired private BorrowingService service;
    @Autowired private BookRepository books;
    @Autowired private BorrowingRepository borrowings;
    @Autowired private MemberRepository members;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    private Book book;
    private Member firstMember;
    private Member secondMember;

    @BeforeEach
    void setUp() {
        book = new Book();
        book.setIsbn("race-" + UUID.randomUUID().toString().substring(0, 20));
        book.setTitle("Concurrent borrowing fixture");
        book.setAuthor("Test Author");
        book.setGenre("Engineering");
        book.setPublishedYear(2024);
        book.setAvailable(true);
        book = books.saveAndFlush(book);
        firstMember = saveMember("First concurrent member");
        secondMember = saveMember("Second concurrent member");
    }

    @AfterEach
    void tearDown() {
        // runContended joins or cancels its workers before fixture cleanup can begin.
        for (Borrowing borrowing : borrowings.findAll()) {
            if (book != null && book.getId().equals(borrowing.getBook().getId())) {
                borrowings.deleteById(borrowing.getId());
            }
        }
        if (book != null && book.getId() != null) {
            books.deleteById(book.getId());
        }
        if (firstMember != null) {
            members.deleteById(firstMember.getId());
        }
        if (secondMember != null) {
            members.deleteById(secondMember.getId());
        }
    }

    @Test
    void concurrentBorrowing_shouldCreateOnlyOneLoan() throws Exception {
        long countBefore = borrowings.count();

        Long borrowingId = runContended(
                () -> service.borrowBook(book.getId(), firstMember.getId()),
                () -> service.borrowBook(book.getId(), secondMember.getId()),
                "Book is already borrowed.");

        assertEquals(countBefore + 1, borrowings.count());
        Borrowing persisted = borrowings.findById(borrowingId).orElseThrow();
        assertEquals(book.getId(), persisted.getBook().getId());
        assertEquals(firstMember.getId(), persisted.getMember().getId());
        assertEquals(TODAY, persisted.getBorrowDate());
        assertEquals(TODAY.plusDays(14), persisted.getDueDate());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void concurrentReturn_shouldPreserveTheFirstReturnAndFine() throws Exception {
        Borrowing original = service.borrowBook(book.getId(), firstMember.getId());
        original.setBorrowDate(TODAY.minusDays(17));
        original.setDueDate(TODAY.minusDays(3));
        borrowings.saveAndFlush(original);
        long countBefore = borrowings.count();

        Long returnedId = runContended(
                () -> service.returnBook(original.getId()),
                () -> service.returnBook(original.getId()),
                "Borrowing has already been returned.");

        assertEquals(original.getId(), returnedId);
        assertEquals(countBefore, borrowings.count());
        Borrowing persisted = borrowings.findById(returnedId).orElseThrow();
        assertEquals(TODAY, persisted.getReturnDate());
        assertEquals(new BigDecimal("1.50"), persisted.getFineAmount());
        assertEquals(original.getDueDate(), persisted.getDueDate());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    private Long runContended(Supplier<Borrowing> firstOperation,
                              Supplier<Borrowing> competingOperation,
                              String expectedConflict) throws Exception {
        CountDownLatch firstFlushed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);
        AtomicInteger firstSession = new AtomicInteger();
        AtomicInteger contenderSession = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<Long> first = null;
        Future<RuntimeException> contender = null;
        try {
            first = workers.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                firstSession.set(currentSessionId());
                Borrowing result = firstOperation.get();
                entityManager.flush();
                firstFlushed.countDown();
                awaitLatch(releaseFirst, "First transaction was not released");
                return result.getId();
            }));
            awaitLatch(firstFlushed, "First transaction did not reach its held commit point");

            contender = workers.submit(() -> {
                try {
                    new TransactionTemplate(transactionManager).execute(status -> {
                        contenderSession.set(currentSessionId());
                        contenderStarted.countDown();
                        return competingOperation.get().getId();
                    });
                    return null;
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            awaitLatch(contenderStarted, "Competing transaction did not start");
            assertNotEquals(firstSession.get(), contenderSession.get());
            awaitSelectForUpdateBlockedBy(contenderSession.get(), firstSession.get(), contender);

            releaseFirst.countDown();
            Long successfulId = first.get(WAIT_SECONDS, TimeUnit.SECONDS);
            RuntimeException failure = contender.get(WAIT_SECONDS, TimeUnit.SECONDS);
            IllegalStateException conflict = assertInstanceOf(IllegalStateException.class, failure);
            assertEquals(expectedConflict, conflict.getMessage());
            return successfulId;
        } finally {
            releaseFirst.countDown();
            if (first != null && !first.isDone()) {
                first.cancel(true);
            }
            if (contender != null && !contender.isDone()) {
                contender.cancel(true);
            }
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS),
                    "Concurrency workers did not terminate after cancellation");
        }
    }

    private int currentSessionId() {
        return ((Number) entityManager.createNativeQuery("SELECT SESSION_ID()")
                .getSingleResult()).intValue();
    }

    private void awaitSelectForUpdateBlockedBy(int contenderSession, int firstSession,
                                               Future<?> contender) throws Exception {
        // H2 metadata confirms actual contention rather than relying on thread timing.
        // https://h2database.com/html/systemtables.html#sessions
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String lastStatement = null;
        try (Connection observer = dataSource.getConnection();
             PreparedStatement query = observer.prepareStatement(
                     "SELECT BLOCKER_ID, EXECUTING_STATEMENT FROM INFORMATION_SCHEMA.SESSIONS "
                             + "WHERE SESSION_ID = ?")) {
            query.setInt(1, contenderSession);
            query.setQueryTimeout(2);
            while (System.nanoTime() < deadline) {
                try (ResultSet sessions = query.executeQuery()) {
                    if (sessions.next()) {
                        int blocker = sessions.getInt("BLOCKER_ID");
                        lastStatement = sessions.getString("EXECUTING_STATEMENT");
                        if (blocker == firstSession && lastStatement != null
                                && lastStatement.toLowerCase(Locale.ROOT).contains("for update")) {
                            return;
                        }
                    }
                }
                assertFalse(contender.isDone(),
                        "Competing operation completed before an overlapping row-lock wait was observed");
                TimeUnit.MILLISECONDS.sleep(10);
            }
        }
        fail("No SELECT FOR UPDATE blocked by the first transaction was observed; last SQL: "
                + lastStatement);
    }

    private static void awaitLatch(CountDownLatch latch, String failureMessage) {
        try {
            assertTrue(latch.await(WAIT_SECONDS, TimeUnit.SECONDS), failureMessage);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failureMessage, interrupted);
        }
    }

    private Member saveMember(String name) {
        Member member = new Member();
        member.setName(name);
        member.setEmail(UUID.randomUUID() + "@example.com");
        member.setMembershipDate(TODAY.minusMonths(1));
        member.setActive(true);
        return members.saveAndFlush(member);
    }
}
