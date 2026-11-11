package com.library.api.service;

import com.library.api.entity.Book;
import com.library.api.entity.Borrowing;
import com.library.api.entity.Member;
import com.library.api.entity.Reservation;
import com.library.api.entity.ReservationStatus;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import com.library.api.repository.ReservationRepository;
import com.library.api.support.FixedClockConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservationservicetest;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@Import(FixedClockConfiguration.class)
class ReservationServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 29);
    private static final String AVAILABLE = "Book is available for direct borrowing.";
    private static final String DUPLICATE = "An active reservation already exists for this book and member.";
    private static final String CANCELLED = "Reservation has already been cancelled.";

    @Autowired private ReservationService service;
    @Autowired private BorrowingService borrowingService;
    @Autowired private ReservationRepository reservations;
    @Autowired private BorrowingRepository borrowings;
    @Autowired private BookRepository books;
    @Autowired private MemberRepository members;

    private Book book;
    private Member member;
    private Member otherMember;

    @BeforeEach
    void setUp() {
        book = new Book();
        book.setIsbn("reserve-" + UUID.randomUUID().toString().substring(0, 20));
        book.setTitle("Reservation service fixture");
        book.setAuthor("Test Author");
        book.setGenre("Engineering");
        book.setPublishedYear(2024);
        book.setAvailable(false);
        book = books.saveAndFlush(book);
        member = saveMember("Reservation member");
        otherMember = saveMember("Other reservation member");
    }

    @AfterEach
    void tearDown() {
        // Service calls commit or roll back before assertions; cleanup follows fresh reads.
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
        if (otherMember != null) {
            members.deleteById(otherMember.getId());
        }
    }

    @Test
    void ac1_availableBook_isRejectedWithoutStoredChanges() {
        book.setAvailable(true);
        books.saveAndFlush(book);
        long countBefore = reservations.count();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.reserveBook(book.getId(), member.getId()));

        assertEquals(AVAILABLE, failure.getMessage());
        assertEquals(countBefore, reservations.count());
        assertExistingBookAndMemberUnchanged();
    }

    @ParameterizedTest(name = "AC-2: member active={0} remains eligible")
    @ValueSource(booleans = {true, false})
    void ac2_unavailableBook_createsActiveReservationForExistingMember(boolean activeMember) {
        member.setActive(activeMember);
        members.saveAndFlush(member);
        long borrowingCount = borrowings.count();

        Reservation result = service.reserveBook(book.getId(), member.getId());

        assertNotNull(result.getId());
        assertEquals(ReservationStatus.ACTIVE, result.getStatus());
        assertEquals(TODAY, result.getReservationDate());
        assertNull(result.getCancelledDate());
        Reservation persisted = reservations.findById(result.getId()).orElseThrow();
        assertEquals(book.getId(), persisted.getBook().getId());
        assertEquals(member.getId(), persisted.getMember().getId());
        assertEquals(TODAY, persisted.getReservationDate());
        assertEquals(ReservationStatus.ACTIVE, persisted.getStatus());
        assertNull(persisted.getCancelledDate());
        assertEquals(borrowingCount, borrowings.count());
        assertExistingBookAndMemberUnchanged();
    }

    @Test
    void ac2_currentBorrower_canReserveAndCancelWithoutChangingTheLoan() {
        Borrowing loan = borrowFixture();
        long borrowingCount = borrowings.count();

        Reservation result = service.reserveBook(book.getId(), member.getId());
        assertEquals(ReservationStatus.ACTIVE, result.getStatus());
        assertBorrowingUnchanged(loan);
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());

        service.cancelReservation(result.getId());

        assertEquals(ReservationStatus.CANCELLED,
                reservations.findById(result.getId()).orElseThrow().getStatus());
        assertEquals(borrowingCount, borrowings.count());
        assertBorrowingUnchanged(loan);
        assertExistingBookAndMemberUnchanged();
    }

    @Test
    void ac3_duplicateActivePair_isRejectedButAnotherMemberCanReserve() {
        Reservation first = service.reserveBook(book.getId(), member.getId());
        long countBefore = reservations.count();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.reserveBook(book.getId(), member.getId()));

        assertEquals(DUPLICATE, failure.getMessage());
        assertEquals(countBefore, reservations.count());
        assertEquals(ReservationStatus.ACTIVE,
                reservations.findById(first.getId()).orElseThrow().getStatus());
        Reservation other = service.reserveBook(book.getId(), otherMember.getId());
        assertNotEquals(first.getId(), other.getId());
        assertEquals(otherMember.getId(), other.getMember().getId());
        assertEquals(countBefore + 1, reservations.count());
        assertExistingBookAndMemberUnchanged();
    }

    @Test
    void ac4_cancellation_preservesIdentityAndOriginalDate() {
        Reservation original = service.reserveBook(book.getId(), member.getId());
        LocalDate originalDate = TODAY.minusDays(5);
        original.setReservationDate(originalDate);
        reservations.saveAndFlush(original);
        long countBefore = reservations.count();

        Reservation result = service.cancelReservation(original.getId());

        assertEquals(original.getId(), result.getId());
        assertEquals(ReservationStatus.CANCELLED, result.getStatus());
        assertEquals(TODAY, result.getCancelledDate());
        Reservation persisted = reservations.findById(original.getId()).orElseThrow();
        assertEquals(originalDate, persisted.getReservationDate());
        assertEquals(book.getId(), persisted.getBook().getId());
        assertEquals(member.getId(), persisted.getMember().getId());
        assertEquals(ReservationStatus.CANCELLED, persisted.getStatus());
        assertEquals(TODAY, persisted.getCancelledDate());
        assertEquals(countBefore, reservations.count());
        assertExistingBookAndMemberUnchanged();
    }

    @Test
    void ac4_repeatedCancellation_preservesTheEarlierCancellationDate() {
        Reservation original = service.reserveBook(book.getId(), member.getId());
        original.setReservationDate(TODAY.minusDays(5));
        original.setStatus(ReservationStatus.CANCELLED);
        LocalDate firstCancellationDate = TODAY.minusDays(2);
        original.setCancelledDate(firstCancellationDate);
        reservations.saveAndFlush(original);
        long countBefore = reservations.count();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.cancelReservation(original.getId()));

        assertEquals(CANCELLED, failure.getMessage());
        Reservation persisted = reservations.findById(original.getId()).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, persisted.getStatus());
        assertEquals(firstCancellationDate, persisted.getCancelledDate());
        assertEquals(original.getReservationDate(), persisted.getReservationDate());
        assertEquals(countBefore, reservations.count());
        assertExistingBookAndMemberUnchanged();
    }

    @Test
    void ac5_repeatedCancellationAndRecreation_retainsEveryHistoricalRow() {
        Reservation first = service.reserveBook(book.getId(), member.getId());
        service.cancelReservation(first.getId());
        Reservation second = service.reserveBook(book.getId(), member.getId());
        service.cancelReservation(second.getId());

        Reservation third = service.reserveBook(book.getId(), member.getId());

        assertNotEquals(first.getId(), second.getId());
        assertNotEquals(second.getId(), third.getId());
        List<Reservation> history = service.getReservationsForMember(member.getId());
        assertEquals(List.of(first.getId(), second.getId(), third.getId()),
                history.stream().map(Reservation::getId).toList());
        assertEquals(List.of(ReservationStatus.CANCELLED, ReservationStatus.CANCELLED,
                        ReservationStatus.ACTIVE),
                history.stream().map(Reservation::getStatus).toList());
        assertEquals(TODAY, history.get(0).getCancelledDate());
        assertEquals(TODAY, history.get(1).getCancelledDate());
        assertNull(history.get(2).getCancelledDate());
        assertExistingBookAndMemberUnchanged();
    }

    @Test
    void ac6_history_includesAllStatusesForOnlyTheRequestedMemberInIdOrder() {
        Reservation cancelled = service.reserveBook(book.getId(), member.getId());
        service.cancelReservation(cancelled.getId());
        service.reserveBook(book.getId(), otherMember.getId());
        Reservation active = service.reserveBook(book.getId(), member.getId());

        List<Reservation> history = service.getReservationsForMember(member.getId());

        assertEquals(List.of(cancelled.getId(), active.getId()),
                history.stream().map(Reservation::getId).toList());
        assertEquals(List.of(ReservationStatus.CANCELLED, ReservationStatus.ACTIVE),
                history.stream().map(Reservation::getStatus).toList());
        assertTrue(history.stream().allMatch(reservation ->
                member.getId().equals(reservation.getMember().getId())));
    }

    @Test
    void ac6_memberWithoutHistory_getsEmptyList() {
        service.reserveBook(book.getId(), otherMember.getId());

        assertTrue(service.getReservationsForMember(member.getId()).isEmpty());
    }

    @Test
    void ac6_missingMember_isNotTreatedAsAnEmptyHistory() {
        ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class,
                () -> service.getReservationsForMember(Long.MAX_VALUE));

        assertEquals("Member with id " + Long.MAX_VALUE + " was not found", failure.getMessage());
    }

    @ParameterizedTest(name = "AC-7: bookExists={0}, memberExists={1}, available={2}")
    @CsvSource({
            "false, true, false, Book",
            "false, false, false, Book",
            "true, false, false, Member",
            "true, false, true, Member"
    })
    void ac7_missingResources_followTheSpecifiedPrecedence(boolean bookExists, boolean memberExists,
                                                           boolean available, String expectedResource) {
        book.setAvailable(available);
        books.saveAndFlush(book);
        Long bookId = bookExists ? book.getId() : Long.MAX_VALUE;
        Long memberId = memberExists ? member.getId() : Long.MAX_VALUE;
        long countBefore = reservations.count();

        ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class,
                () -> service.reserveBook(bookId, memberId));

        assertEquals(expectedResource + " with id " + Long.MAX_VALUE + " was not found",
                failure.getMessage());
        assertEquals(countBefore, reservations.count());
        assertExistingBookAndMemberUnchanged();
    }

    @Test
    void ac8_missingReservation_doesNotChangeExistingHistory() {
        Reservation original = service.reserveBook(book.getId(), member.getId());
        long countBefore = reservations.count();

        ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class,
                () -> service.cancelReservation(Long.MAX_VALUE));

        assertEquals("Reservation with id " + Long.MAX_VALUE + " was not found", failure.getMessage());
        Reservation persisted = reservations.findById(original.getId()).orElseThrow();
        assertEquals(ReservationStatus.ACTIVE, persisted.getStatus());
        assertEquals(original.getReservationDate(), persisted.getReservationDate());
        assertNull(persisted.getCancelledDate());
        assertEquals(countBefore, reservations.count());
        assertExistingBookAndMemberUnchanged();
    }

    @Test
    void ac9_bookReturn_preservesActiveReservationAndAllowsCancellation() {
        Borrowing loan = borrowFixture();
        Reservation original = service.reserveBook(book.getId(), member.getId());

        Borrowing returnedLoan = borrowingService.returnBook(loan.getId());

        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
        Reservation afterReturn = reservations.findById(original.getId()).orElseThrow();
        assertEquals(ReservationStatus.ACTIVE, afterReturn.getStatus());
        assertEquals(original.getReservationDate(), afterReturn.getReservationDate());
        assertNull(afterReturn.getCancelledDate());
        long countBeforeRejectedCreation = reservations.count();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.reserveBook(book.getId(), member.getId()));
        assertEquals(AVAILABLE, failure.getMessage());
        assertEquals(countBeforeRejectedCreation, reservations.count());

        service.cancelReservation(original.getId());

        assertEquals(ReservationStatus.CANCELLED,
                reservations.findById(original.getId()).orElseThrow().getStatus());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
        assertBorrowingUnchanged(returnedLoan);
    }

    private Member saveMember(String name) {
        Member saved = new Member();
        saved.setName(name);
        saved.setEmail(UUID.randomUUID() + "@example.com");
        saved.setMembershipDate(TODAY.minusMonths(1));
        saved.setActive(true);
        return members.saveAndFlush(saved);
    }

    private Borrowing borrowFixture() {
        book.setAvailable(true);
        books.saveAndFlush(book);
        Borrowing loan = borrowingService.borrowBook(book.getId(), member.getId());
        book = books.findById(book.getId()).orElseThrow();
        return loan;
    }

    private void assertExistingBookAndMemberUnchanged() {
        Book persistedBook = books.findById(book.getId()).orElseThrow();
        assertEquals(book.getIsbn(), persistedBook.getIsbn());
        assertEquals(book.getTitle(), persistedBook.getTitle());
        assertEquals(book.getAuthor(), persistedBook.getAuthor());
        assertEquals(book.getGenre(), persistedBook.getGenre());
        assertEquals(book.getPublishedYear(), persistedBook.getPublishedYear());
        assertEquals(book.isAvailable(), persistedBook.isAvailable());
        Member persistedMember = members.findById(member.getId()).orElseThrow();
        assertEquals(member.getName(), persistedMember.getName());
        assertEquals(member.getEmail(), persistedMember.getEmail());
        assertEquals(member.getMembershipDate(), persistedMember.getMembershipDate());
        assertEquals(member.isActive(), persistedMember.isActive());
    }

    private void assertBorrowingUnchanged(Borrowing original) {
        Borrowing persisted = borrowings.findById(original.getId()).orElseThrow();
        assertEquals(original.getBook().getId(), persisted.getBook().getId());
        assertEquals(original.getMember().getId(), persisted.getMember().getId());
        assertEquals(original.getBorrowDate(), persisted.getBorrowDate());
        assertEquals(original.getDueDate(), persisted.getDueDate());
        assertEquals(original.getReturnDate(), persisted.getReturnDate());
        assertEquals(original.getFineAmount(), persisted.getFineAmount());
    }
}
