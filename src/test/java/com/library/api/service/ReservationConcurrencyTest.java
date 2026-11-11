package com.library.api.service;

import com.library.api.entity.Book;
import com.library.api.entity.Borrowing;
import com.library.api.entity.Member;
import com.library.api.entity.Reservation;
import com.library.api.entity.ReservationStatus;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import com.library.api.repository.ReservationRepository;
import com.library.api.support.FixedClockConfiguration;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservationconcurrencytest;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.h2.console.enabled=false"
})
@Import(FixedClockConfiguration.class)
class ReservationConcurrencyTest {
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 29);
    private static final int WAIT_SECONDS = 15;
    private static final String AVAILABLE = "Book is available for direct borrowing.";
    private static final String DUPLICATE = "An active reservation already exists for this book and member.";
    private static final String CANCELLED = "Reservation has already been cancelled.";

    @Autowired private ReservationService service;
    @Autowired private BorrowingService borrowingService;
    @Autowired private ReservationRepository reservations;
    @Autowired private BorrowingRepository borrowings;
    @Autowired private BookRepository books;
    @Autowired private MemberRepository members;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    private Book book;
    private Member member;

    @BeforeEach
    void setUp() {
        book = new Book();
        book.setIsbn("res-race-" + UUID.randomUUID().toString().substring(0, 20));
        book.setTitle("Reservation contention fixture");
        book.setAuthor("Test Author");
        book.setGenre("Engineering");
        book.setPublishedYear(2024);
        book.setAvailable(false);
        book = books.saveAndFlush(book);

        member = new Member();
        member.setName("Reservation contention member");
        member.setEmail(UUID.randomUUID() + "@example.com");
        member.setMembershipDate(TODAY.minusMonths(1));
        member.setActive(true);
        member = members.saveAndFlush(member);
    }

    @AfterEach
    void tearDown() {
        // Both worker transactions have finished before fixture cleanup begins.
        if (book != null && book.getId() != null) {
            for (Reservation reservation : reservations.findAll()) {
                if (book.getId().equals(reservation.getBook().getId())) {
                    reservations.deleteById(reservation.getId());
                }
            }
            for (Borrowing borrowing : borrowings.findAll()) {
                if (book.getId().equals(borrowing.getBook().getId())) {
                    borrowings.deleteById(borrowing.getId());
                }
            }
            books.deleteById(book.getId());
        }
        if (member != null) {
            members.deleteById(member.getId());
        }
    }

    @Test
    void ac10_competingCreations_commitOnlyOneActivePair() throws Exception {
        long countBefore = reservations.count();

        Outcomes outcomes = contend(
                () -> service.reserveBook(book.getId(), member.getId()).getId(),
                () -> service.reserveBook(book.getId(), member.getId()).getId());

        Long createdId = successfulId(outcomes.first());
        assertConflict(outcomes.contender(), DUPLICATE);
        assertEquals(countBefore + 1, reservations.count());
        assertActiveReservation(createdId);
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void ac10_competingCancellations_preserveTheFirstCancellation() throws Exception {
        Reservation original = activeReservationWithEarlierDate();
        long countBefore = reservations.count();

        Outcomes outcomes = contend(
                () -> service.cancelReservation(original.getId()).getId(),
                () -> service.cancelReservation(original.getId()).getId());

        assertEquals(original.getId(), successfulId(outcomes.first()));
        assertConflict(outcomes.contender(), CANCELLED);
        assertCancelledReservation(original);
        assertEquals(countBefore, reservations.count());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void ac10_cancellationBeforeCreation_allowsANewActiveRecord() throws Exception {
        Reservation original = activeReservationWithEarlierDate();
        long countBefore = reservations.count();

        Outcomes outcomes = contend(
                () -> service.cancelReservation(original.getId()).getId(),
                () -> service.reserveBook(book.getId(), member.getId()).getId());

        assertEquals(original.getId(), successfulId(outcomes.first()));
        Long createdId = successfulId(outcomes.contender());
        assertNotEquals(original.getId(), createdId);
        assertCancelledReservation(original);
        assertActiveReservation(createdId);
        assertEquals(countBefore + 1, reservations.count());
        List<Reservation> history = service.getReservationsForMember(member.getId());
        assertEquals(List.of(original.getId(), createdId),
                history.stream().map(Reservation::getId).toList());
        assertEquals(1, history.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.ACTIVE).count());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void ac10_creationBeforeCancellation_rejectsTheStillActiveDuplicate() throws Exception {
        Reservation original = activeReservationWithEarlierDate();
        long countBefore = reservations.count();

        Outcomes outcomes = contend(
                () -> service.reserveBook(book.getId(), member.getId()).getId(),
                () -> service.cancelReservation(original.getId()).getId());

        assertConflict(outcomes.first(), DUPLICATE);
        assertEquals(original.getId(), successfulId(outcomes.contender()));
        assertCancelledReservation(original);
        assertEquals(countBefore, reservations.count());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void ac10_returnBeforeCreation_rejectsTheNowAvailableBook() throws Exception {
        Borrowing loan = borrowFixture();
        long countBefore = reservations.count();

        Outcomes outcomes = contend(
                () -> borrowingService.returnBook(loan.getId()).getId(),
                () -> service.reserveBook(book.getId(), member.getId()).getId());

        assertEquals(loan.getId(), successfulId(outcomes.first()));
        assertConflict(outcomes.contender(), AVAILABLE);
        assertEquals(countBefore, reservations.count());
        assertReturnedLoan(loan);
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void ac10_creationBeforeReturn_keepsTheReservationActiveAfterTheReturn() throws Exception {
        Borrowing loan = borrowFixture();
        long countBefore = reservations.count();

        Outcomes outcomes = contend(
                () -> service.reserveBook(book.getId(), member.getId()).getId(),
                () -> borrowingService.returnBook(loan.getId()).getId());

        Long createdId = successfulId(outcomes.first());
        assertEquals(loan.getId(), successfulId(outcomes.contender()));
        assertEquals(countBefore + 1, reservations.count());
        assertActiveReservation(createdId);
        assertReturnedLoan(loan);
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    private Outcomes contend(Supplier<Long> firstOperation, Supplier<Long> competingOperation)
            throws Exception {
        CountDownLatch firstCompletedOperation = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);
        AtomicInteger firstSession = new AtomicInteger();
        AtomicInteger contenderSession = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<Outcome> first = null;
        Future<Outcome> contender = null;
        try {
            first = workers.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                firstSession.set(currentSessionId());
                Outcome outcome = invoke(firstOperation, status);
                if (outcome.conflict() == null) {
                    entityManager.flush();
                }
                firstCompletedOperation.countDown();
                awaitLatch(releaseFirst, "First transaction was not released");
                return outcome;
            }));
            awaitLatch(firstCompletedOperation, "First operation did not reach its held completion point");

            contender = workers.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                contenderSession.set(currentSessionId());
                contenderStarted.countDown();
                return invoke(competingOperation, status);
            }));
            awaitLatch(contenderStarted, "Competing transaction did not start");
            assertNotEquals(firstSession.get(), contenderSession.get());
            awaitSelectForUpdateBlockedBy(contenderSession.get(), firstSession.get(), contender);

            releaseFirst.countDown();
            return new Outcomes(first.get(WAIT_SECONDS, TimeUnit.SECONDS),
                    contender.get(WAIT_SECONDS, TimeUnit.SECONDS));
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
                    "Reservation contention workers did not terminate after cancellation");
        }
    }

    private Outcome invoke(Supplier<Long> operation, TransactionStatus status) {
        try {
            return new Outcome(operation.get(), null);
        } catch (IllegalStateException conflict) {
            // A rejected service call marks the shared transaction rollback-only. Make the
            // outer rollback intentional while retaining its lock until the contender is seen.
            status.setRollbackOnly();
            return new Outcome(null, conflict);
        }
    }

    private int currentSessionId() {
        return ((Number) entityManager.createNativeQuery("SELECT SESSION_ID()")
                .getSingleResult()).intValue();
    }

    private void awaitSelectForUpdateBlockedBy(int contenderSession, int firstSession,
                                               Future<?> contender) throws Exception {
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

    private static Long successfulId(Outcome outcome) {
        assertNull(outcome.conflict(), "Expected a successful transaction");
        assertNotNull(outcome.id());
        return outcome.id();
    }

    private static void assertConflict(Outcome outcome, String expectedMessage) {
        assertNull(outcome.id());
        assertNotNull(outcome.conflict(), "Expected a domain conflict");
        assertEquals(expectedMessage, outcome.conflict().getMessage());
    }

    private Reservation activeReservationWithEarlierDate() {
        Reservation reservation = service.reserveBook(book.getId(), member.getId());
        reservation.setReservationDate(TODAY.minusDays(5));
        return reservations.saveAndFlush(reservation);
    }

    private Borrowing borrowFixture() {
        book.setAvailable(true);
        books.saveAndFlush(book);
        return borrowingService.borrowBook(book.getId(), member.getId());
    }

    private void assertActiveReservation(Long id) {
        Reservation persisted = reservations.findById(id).orElseThrow();
        assertEquals(book.getId(), persisted.getBook().getId());
        assertEquals(member.getId(), persisted.getMember().getId());
        assertEquals(TODAY, persisted.getReservationDate());
        assertEquals(ReservationStatus.ACTIVE, persisted.getStatus());
        assertNull(persisted.getCancelledDate());
    }

    private void assertCancelledReservation(Reservation original) {
        Reservation persisted = reservations.findById(original.getId()).orElseThrow();
        assertEquals(original.getReservationDate(), persisted.getReservationDate());
        assertEquals(TODAY, persisted.getCancelledDate());
        assertEquals(ReservationStatus.CANCELLED, persisted.getStatus());
        assertEquals(book.getId(), persisted.getBook().getId());
        assertEquals(member.getId(), persisted.getMember().getId());
    }

    private void assertReturnedLoan(Borrowing original) {
        Borrowing persisted = borrowings.findById(original.getId()).orElseThrow();
        assertEquals(TODAY, persisted.getReturnDate());
        assertEquals(new BigDecimal("0.00"), persisted.getFineAmount());
        assertEquals(original.getBorrowDate(), persisted.getBorrowDate());
        assertEquals(original.getDueDate(), persisted.getDueDate());
    }

    private record Outcome(Long id, IllegalStateException conflict) { }

    private record Outcomes(Outcome first, Outcome contender) { }
}
