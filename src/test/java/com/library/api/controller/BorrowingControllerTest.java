package com.library.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.api.entity.Book;
import com.library.api.entity.Borrowing;
import com.library.api.entity.Member;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import com.library.api.support.FixedClockConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:borrowingcontrollertest;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
class BorrowingControllerTest {
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 29);
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookRepository books;
    @Autowired private MemberRepository members;
    @Autowired private BorrowingRepository borrowings;

    private Book book;
    private Member member;

    @BeforeEach
    void setUp() {
        book = new Book();
        book.setIsbn("http-" + UUID.randomUUID().toString().substring(0, 20));
        book.setTitle("HTTP borrowing fixture");
        book.setAuthor("Test Author");
        book.setGenre("Engineering");
        book.setPublishedYear(2026);
        book = books.saveAndFlush(book);

        member = new Member();
        member.setName("HTTP test member");
        member.setEmail(UUID.randomUUID() + "@example.com");
        member.setMembershipDate(TODAY);
        member = members.saveAndFlush(member);
    }

    @AfterEach
    void tearDown() {
        // Requests run without a surrounding test transaction, just as real HTTP requests do.
        borrowings.deleteAll();
        books.deleteById(book.getId());
        members.deleteById(member.getId());
    }

    @Test
    void borrowCreatesSerializableResponseAndPersistsAvailability() throws Exception {
        String body = mockMvc.perform(post("/borrowings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(book.getId(), member.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.bookId").value(book.getId()))
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.borrowDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.dueDate").value(TODAY.plusDays(14).toString()))
                .andExpect(jsonPath("$.returnDate").isEmpty())
                .andExpect(jsonPath("$.fineAmount").value(0.00))
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(body);
        assertEquals(LocalDate.parse(result.get("borrowDate").asText()).plusDays(14),
                LocalDate.parse(result.get("dueDate").asText()));
        assertTrue(borrowings.existsById(result.get("id").asLong()));
        Borrowing persisted = borrowings.findById(result.get("id").asLong()).orElseThrow();
        assertEquals(TODAY, persisted.getBorrowDate());
        assertEquals(TODAY.plusDays(14), persisted.getDueDate());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bookId", "memberId"})
    void fractionalIdsAreRejectedWithoutBorrowingAnotherRecord(String field) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
                "bookId", book.getId(), "memberId", member.getId()));
        Long integerId = (Long) body.get(field);
        body.put(field, integerId.doubleValue() + 0.5);

        mockMvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertEquals(0, borrowings.count());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void doubleBorrowReturnsConflictWithoutCreatingAnotherRecord() throws Exception {
        mockMvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(book.getId(), member.getId())))
                .andExpect(status().isCreated());
        long countAfterFirstBorrow = borrowings.count();

        mockMvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(book.getId(), member.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Book is already borrowed."));

        assertEquals(countAfterFirstBorrow, borrowings.count());
    }

    @Test
    void missingBookReturnsNotFound() throws Exception {
        mockMvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(Long.MAX_VALUE, member.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Book with id " + Long.MAX_VALUE + " was not found"));
    }

    @Test
    void missingMemberReturnsNotFoundWithoutChangingBook() throws Exception {
        mockMvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(book.getId(), Long.MAX_VALUE)))
                .andExpect(status().isNotFound());

        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
        assertEquals(0, borrowings.count());
    }

    @Test
    void missingAndNonpositiveIdsReturnValidationErrors() throws Exception {
        mockMvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bookId").exists())
                .andExpect(jsonPath("$.errors.memberId").exists());
        mockMvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(0L, -1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bookId").exists())
                .andExpect(jsonPath("$.errors.memberId").exists());

        assertEquals(0, borrowings.count());
    }

    @Test
    void malformedBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editingBookCannotMakeAnActiveLoanAvailable() throws Exception {
        mockMvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(book.getId(), member.getId())))
                .andExpect(status().isCreated());
        Map<String, Object> update = new java.util.LinkedHashMap<>(Map.of(
                "isbn", book.getIsbn(), "title", "Updated title", "author", book.getAuthor(),
                "genre", book.getGenre(), "publishedYear", book.getPublishedYear()));

        // Omitting available defaults to true in the original Lab 1 PUT contract.
        mockMvc.perform(put("/books/{id}", book.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isConflict());
        update.put("available", true);
        mockMvc.perform(put("/books/{id}", book.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isConflict());

        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
        assertEquals(book.getTitle(), books.findById(book.getId()).orElseThrow().getTitle());

        update.put("available", false);
        mockMvc.perform(put("/books/{id}", book.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void returnRestoresAvailabilityAndRejectsRepeatedReturn() throws Exception {
        Long borrowingId = borrowFixture();

        mockMvc.perform(put("/borrowings/{id}/return", borrowingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(borrowingId))
                .andExpect(jsonPath("$.returnDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.fineAmount").value(0.00));
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());

        mockMvc.perform(put("/borrowings/{id}/return", borrowingId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Borrowing has already been returned."));

        // Returning an old record must also leave a subsequent loan unavailable.
        Long nextBorrowingId = borrowFixture();
        assertFalse(borrowingId.equals(nextBorrowingId));
        mockMvc.perform(put("/borrowings/{id}/return", borrowingId))
                .andExpect(status().isConflict());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
        Borrowing original = borrowings.findById(borrowingId).orElseThrow();
        assertEquals(TODAY, original.getReturnDate());
        assertEquals(new BigDecimal("0.00"), original.getFineAmount());
    }

    @Test
    void returnPersistsOverdueFine() throws Exception {
        Long borrowingId = borrowFixture();
        Borrowing borrowing = borrowings.findById(borrowingId).orElseThrow();
        borrowing.setBorrowDate(TODAY.minusDays(17));
        borrowing.setDueDate(TODAY.minusDays(3));
        borrowings.saveAndFlush(borrowing);

        mockMvc.perform(put("/borrowings/{id}/return", borrowingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.fineAmount").value(1.50));

        Borrowing returned = borrowings.findById(borrowingId).orElseThrow();
        assertEquals(TODAY, returned.getReturnDate());
        assertEquals(new BigDecimal("1.50"), returned.getFineAmount());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void missingBorrowingReturnsNotFound() throws Exception {
        mockMvc.perform(put("/borrowings/{id}/return", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Borrowing with id " + Long.MAX_VALUE + " was not found"));
    }

    @Test
    void activeListIsScopedToMemberAndExcludesReturnedBorrowings() throws Exception {
        Member otherMember = new Member();
        otherMember.setName("Other query member");
        otherMember.setEmail(UUID.randomUUID() + "@example.com");
        otherMember.setMembershipDate(TODAY);
        otherMember = members.saveAndFlush(otherMember);
        try {
            mockMvc.perform(get("/members/{id}/borrowings", member.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
            Long borrowingId = borrowFixture();

            mockMvc.perform(get("/members/{id}/borrowings", member.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(borrowingId))
                    .andExpect(jsonPath("$[0].bookId").value(book.getId()))
                    .andExpect(jsonPath("$[0].memberId").value(member.getId()))
                    .andExpect(jsonPath("$[0].returnDate").isEmpty());
            mockMvc.perform(get("/members/{id}/borrowings", otherMember.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            mockMvc.perform(put("/borrowings/{id}/return", borrowingId))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/members/{id}/borrowings", member.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        } finally {
            members.deleteById(otherMember.getId());
        }
    }

    @Test
    void activeListForMissingMemberReturnsNotFound() throws Exception {
        mockMvc.perform(get("/members/{id}/borrowings", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Member with id " + Long.MAX_VALUE + " was not found"));
    }

    private Long borrowFixture() throws Exception {
        String body = mockMvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(book.getId(), member.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private String request(Long bookId, Long memberId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("bookId", bookId, "memberId", memberId));
    }
}
