package com.library.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservationcontrollertest;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
class ReservationControllerTest {
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 29);
    private static final String AVAILABLE = "Book is available for direct borrowing.";
    private static final String DUPLICATE = "An active reservation already exists for this book and member.";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private BookRepository books;
    @Autowired private MemberRepository members;
    @Autowired private BorrowingRepository borrowings;
    @Autowired private ReservationRepository reservations;

    private final List<Book> fixtureBooks = new ArrayList<>();
    private final List<Member> fixtureMembers = new ArrayList<>();
    private Book book;
    private Member member;

    @BeforeEach
    void setUp() {
        book = saveBook(false);
        member = saveMember(true);
    }

    @AfterEach
    void tearDown() {
        reservations.deleteAll();
        borrowings.deleteAll();
        books.deleteAll(fixtureBooks);
        members.deleteAll(fixtureMembers);
    }

    @Test
    void ac1_availableBookReturnsExactConflictWithoutChanges() throws Exception {
        book.setAvailable(true);
        books.saveAndFlush(book);

        expectCreationError(book.getId(), member.getId(), 409, AVAILABLE);

        assertEquals(0, reservations.count());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ac2_createsServerControlledStateForAnyExistingMember(boolean active) throws Exception {
        member.setActive(active);
        members.saveAndFlush(member);
        Map<String, Object> body = requestBody(book.getId(), member.getId());
        body.put("id", Long.MAX_VALUE);
        body.put("status", "CANCELLED");
        body.put("reservationDate", "1900-01-01");
        body.put("cancelledDate", "1900-01-02");
        JsonNode response = json.readTree(mvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString());

        Set<String> fields = new HashSet<>();
        response.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("id", "bookId", "memberId", "reservationDate", "status", "cancelledDate"), fields);
        assertNotEquals(Long.MAX_VALUE, response.get("id").asLong());
        assertEquals(book.getId().longValue(), response.get("bookId").asLong());
        assertEquals(member.getId().longValue(), response.get("memberId").asLong());
        assertEquals(TODAY.toString(), response.get("reservationDate").asText());
        assertEquals("ACTIVE", response.get("status").asText());
        assertTrue(response.get("cancelledDate").isNull());
        Reservation saved = reservations.findById(response.get("id").asLong()).orElseThrow();
        assertEquals(TODAY, saved.getReservationDate());
        assertEquals(ReservationStatus.ACTIVE, saved.getStatus());
        assertNull(saved.getCancelledDate());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
        assertEquals(active, members.findById(member.getId()).orElseThrow().isActive());
        assertEquals(0, borrowings.count());
    }

    @Test
    void ac3_rejectsOnlyTheSameActivePair() throws Exception {
        long firstId = reserve(book, member);
        expectCreationError(book.getId(), member.getId(), 409, DUPLICATE);
        long otherId = reserve(book, saveMember(true));

        assertNotEquals(firstId, otherId);
        assertEquals(2, reservations.count());
        assertEquals(ReservationStatus.ACTIVE, reservations.findById(firstId).orElseThrow().getStatus());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void ac4_cancellationRetainsHistoryAndDoesNotOverwriteEarlierCancellation() throws Exception {
        long id = reserve(book, member);
        Reservation earlier = reservations.findById(id).orElseThrow();
        earlier.setReservationDate(TODAY.minusDays(7));
        reservations.saveAndFlush(earlier);

        mvc.perform(put("/reservations/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.reservationDate").value(TODAY.minusDays(7).toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledDate").value(TODAY.toString()));
        Reservation cancelled = reservations.findById(id).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());
        assertEquals(TODAY, cancelled.getCancelledDate());
        cancelled.setCancelledDate(TODAY.minusDays(1));
        reservations.saveAndFlush(cancelled);

        mvc.perform(put("/reservations/{id}/cancel", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Reservation has already been cancelled."));

        assertEquals(TODAY.minusDays(1), reservations.findById(id).orElseThrow().getCancelledDate());
        assertEquals(1, reservations.count());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
        assertEquals(member.getName(), members.findById(member.getId()).orElseThrow().getName());
        assertEquals(0, borrowings.count());
    }

    @Test
    void ac5_multipleCancelledRowsDoNotBlockALaterRequest() throws Exception {
        long first = reserve(book, member);
        mvc.perform(put("/reservations/{id}/cancel", first)).andExpect(status().isOk());
        long second = reserve(book, member);
        mvc.perform(put("/reservations/{id}/cancel", second)).andExpect(status().isOk());
        long third = reserve(book, member);

        assertEquals(3, Set.of(first, second, third).size());
        assertEquals(3, reservations.count());
        mvc.perform(get("/members/{id}/reservations", member.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$[2].status").value("ACTIVE"));
    }

    @Test
    void ac6_listsOnlyTheRequestedMembersCompleteHistoryInIdOrder() throws Exception {
        long active = reserve(book, member);
        reserve(book, saveMember(true));
        long cancelled = reserve(saveBook(false), member);
        mvc.perform(put("/reservations/{id}/cancel", cancelled)).andExpect(status().isOk());

        mvc.perform(get("/members/{id}/reservations", member.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(active))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].cancelledDate").isEmpty())
                .andExpect(jsonPath("$[1].id").value(cancelled))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$[1].cancelledDate").value(TODAY.toString()));
        mvc.perform(get("/members/{id}/reservations", saveMember(true).getId()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void ac7_missingResourcesFollowTheSpecifiedPrecedence() throws Exception {
        expectCreationError(Long.MAX_VALUE, member.getId(), 404, missing("Book", Long.MAX_VALUE));
        expectCreationError(Long.MAX_VALUE, Long.MAX_VALUE, 404, missing("Book", Long.MAX_VALUE));
        expectCreationError(book.getId(), Long.MAX_VALUE, 404, missing("Member", Long.MAX_VALUE));
        book.setAvailable(true);
        books.saveAndFlush(book);
        expectCreationError(book.getId(), Long.MAX_VALUE, 404, missing("Member", Long.MAX_VALUE));
        assertEquals(0, reservations.count());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @ParameterizedTest
    @MethodSource("invalidIdentifiers")
    void ac7_invalidBodyIdentifiersReturn400WithoutStoredChanges(String field, Object value) throws Exception {
        Map<String, Object> body = requestBody(book.getId(), member.getId());
        body.put(field, value);
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertEquals(0, reservations.count());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    private static Stream<Arguments> invalidIdentifiers() {
        List<Object> invalid = Arrays.asList(null, 0L, -1L, 1.5, "not-a-number",
                new BigInteger("9223372036854775808"));
        return Stream.of("bookId", "memberId").flatMap(field ->
                invalid.stream().map(value -> Arguments.of(field, value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bookId", "memberId"})
    void ac7_absentIdentifiersRetainFieldValidationErrors(String field) throws Exception {
        Map<String, Object> body = requestBody(book.getId(), member.getId());
        body.remove(field);
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + field).exists());
        assertEquals(0, reservations.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{invalid}", "[]", ""})
    void ac7_malformedBodiesReturn400(String body) throws Exception {
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, reservations.count());
    }

    @Test
    void ac7_quotedPositiveIntegersKeepExistingBindingBehavior() throws Exception {
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "bookId", book.getId().toString(), "memberId", member.getId().toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(book.getId()))
                .andExpect(jsonPath("$.memberId").value(member.getId()));
        assertEquals(1, reservations.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
    void ac8_nonconvertiblePathIdsReturn400(String id) throws Exception {
        mvc.perform(put("/reservations/{id}/cancel", id)).andExpect(status().isBadRequest());
        mvc.perform(get("/members/{id}/reservations", id)).andExpect(status().isBadRequest());
        assertEquals(0, reservations.count());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MAX_VALUE})
    void ac8_missingNumericPathIdsReturn404(long id) throws Exception {
        mvc.perform(put("/reservations/{id}/cancel", id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(missing("Reservation", id)));
        mvc.perform(get("/members/{id}/reservations", id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(missing("Member", id)));
        assertEquals(0, reservations.count());
    }

    @Test
    void ac9_currentBorrowerCanReserveAndReturnWithoutChangingReservationState() throws Exception {
        book.setAvailable(true);
        books.saveAndFlush(book);
        String loanBody = mvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(requestBody(book.getId(), member.getId()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long borrowingId = json.readTree(loanBody).get("id").asLong();
        Borrowing before = borrowings.findById(borrowingId).orElseThrow();
        long reservationId = reserve(book, member);
        Borrowing afterReservation = borrowings.findById(borrowingId).orElseThrow();
        assertEquals(before.getDueDate(), afterReservation.getDueDate());
        assertNull(afterReservation.getReturnDate());

        mvc.perform(put("/borrowings/{id}/return", borrowingId)).andExpect(status().isOk());
        assertEquals(ReservationStatus.ACTIVE, reservations.findById(reservationId).orElseThrow().getStatus());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
        expectCreationError(book.getId(), member.getId(), 409, AVAILABLE);
        mvc.perform(put("/reservations/{id}/cancel", reservationId)).andExpect(status().isOk());

        assertEquals(ReservationStatus.CANCELLED, reservations.findById(reservationId).orElseThrow().getStatus());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
        Borrowing returned = borrowings.findById(borrowingId).orElseThrow();
        assertEquals(TODAY, returned.getReturnDate());
        assertEquals(before.getFineAmount(), returned.getFineAmount());
        assertEquals(1, borrowings.count());
        assertEquals(1, reservations.count());
    }

    private long reserve(Book targetBook, Member targetMember) throws Exception {
        String body = mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(requestBody(targetBook.getId(), targetMember.getId()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void expectCreationError(Long bookId, Long memberId, int code, String detail) throws Exception {
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(requestBody(bookId, memberId))))
                .andExpect(status().is(code))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(detail));
    }

    private Map<String, Object> requestBody(Long bookId, Long memberId) {
        return new LinkedHashMap<>(Map.of("bookId", bookId, "memberId", memberId));
    }

    private String missing(String type, long id) {
        return type + " with id " + id + " was not found";
    }

    private Book saveBook(boolean available) {
        Book value = new Book();
        value.setIsbn("res-http-" + UUID.randomUUID().toString().substring(0, 16));
        value.setTitle("Reservation HTTP fixture");
        value.setAuthor("Test Author");
        value.setGenre("Engineering");
        value.setPublishedYear(2024);
        value.setAvailable(available);
        Book saved = books.saveAndFlush(value);
        fixtureBooks.add(saved);
        return saved;
    }

    private Member saveMember(boolean active) {
        Member value = new Member();
        value.setName("Reservation HTTP member");
        value.setEmail(UUID.randomUUID() + "@example.com");
        value.setMembershipDate(TODAY.minusMonths(1));
        value.setActive(active);
        Member saved = members.saveAndFlush(value);
        fixtureMembers.add(saved);
        return saved;
    }
}
