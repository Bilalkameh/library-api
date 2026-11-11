package com.library.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.library.api.entity.Borrowing;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import com.library.api.support.FixedClockConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:borrowingendtoendtest;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=never",
        "spring.h2.console.enabled=false"
})
@Import(FixedClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BorrowingEndToEndTest {
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 29);

    @Autowired private TestRestTemplate http;
    @Autowired private BookRepository books;
    @Autowired private MemberRepository members;
    @Autowired private BorrowingRepository borrowings;

    @AfterEach
    void cleanCommittedFixtures() {
        // Real HTTP requests commit in server transactions; test rollback cannot clean them.
        borrowings.deleteAll();
        books.deleteAll();
        members.deleteAll();
    }

    @Test
    void registeringResourcesAndBorrowingPersistsUnavailableBook() {
        Resources resources = registerResources();

        JsonNode loan = borrow(resources);

        assertBookAvailability(resources.bookId(), false);
        assertEquals(1, borrowings.count());
        Borrowing persisted = borrowings.findById(loan.path("id").asLong()).orElseThrow();
        assertEquals(resources.bookId(), persisted.getBook().getId());
        assertEquals(resources.memberId(), persisted.getMember().getId());
        assertEquals(TODAY, persisted.getBorrowDate());
        assertEquals(TODAY.plusDays(14), persisted.getDueDate());
        assertNull(persisted.getReturnDate());
        assertEquals(new BigDecimal("0.00"), persisted.getFineAmount());
    }

    @Test
    void fullBorrowingCyclePersistsReturnAndRestoresBookAvailability() {
        Resources resources = registerResources();
        JsonNode original = borrow(resources);
        long borrowingId = original.path("id").asLong();
        assertBookAvailability(resources.bookId(), false);

        ResponseEntity<JsonNode> response = http.exchange("/borrowings/{id}/return",
                HttpMethod.PUT, HttpEntity.EMPTY, JsonNode.class, borrowingId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode returned = response.getBody();
        assertNotNull(returned);
        assertEquals(borrowingId, returned.path("id").asLong());
        assertEquals(resources.bookId(), returned.path("bookId").asLong());
        assertEquals(resources.memberId(), returned.path("memberId").asLong());
        assertEquals(original.path("borrowDate"), returned.path("borrowDate"));
        assertEquals(original.path("dueDate"), returned.path("dueDate"));
        assertEquals(TODAY.toString(), returned.path("returnDate").asText());
        assertTrue(returned.path("fineAmount").isNumber());
        assertEquals(0, new BigDecimal("0.00").compareTo(returned.path("fineAmount").decimalValue()));
        assertBookAvailability(resources.bookId(), true);
        assertEquals(1, borrowings.count());
        Borrowing persisted = borrowings.findById(borrowingId).orElseThrow();
        assertEquals(TODAY, persisted.getReturnDate());
        assertEquals(new BigDecimal("0.00"), persisted.getFineAmount());
        ResponseEntity<JsonNode> active = http.getForEntity("/members/{id}/borrowings", JsonNode.class,
                resources.memberId());
        assertEquals(HttpStatus.OK, active.getStatusCode());
        assertNotNull(active.getBody());
        assertTrue(active.getBody().isArray());
        assertEquals(0, active.getBody().size());
    }

    @Test
    void borrowingSameBookTwiceReturnsConflictWithoutAnotherLoan() {
        Resources resources = registerResources();
        JsonNode first = borrow(resources);

        ResponseEntity<JsonNode> response = http.postForEntity("/borrowings",
                Map.of("bookId", resources.bookId(), "memberId", resources.memberId()), JsonNode.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getHeaders().getContentType());
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.getHeaders().getContentType()));
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().path("status").asInt());
        assertEquals("Book is already borrowed.", response.getBody().path("detail").asText());
        assertBookAvailability(resources.bookId(), false);
        assertEquals(1, borrowings.count());
        Borrowing persisted = borrowings.findAll().get(0);
        assertEquals(first.path("id").asLong(), persisted.getId());
        assertEquals(TODAY, persisted.getBorrowDate());
        assertEquals(TODAY.plusDays(14), persisted.getDueDate());
        assertNull(persisted.getReturnDate());
        assertEquals(new BigDecimal("0.00"), persisted.getFineAmount());
    }

    private Resources registerResources() {
        String isbn = "lab7-" + UUID.randomUUID().toString().substring(0, 20);
        String email = UUID.randomUUID() + "@example.com";
        ResponseEntity<JsonNode> createdBook = http.postForEntity("/books", Map.of(
                "isbn", isbn, "title", "HTTP borrowing test", "author", "Test Author",
                "genre", "Engineering", "publishedYear", 2024, "available", true), JsonNode.class);
        assertEquals(HttpStatus.CREATED, createdBook.getStatusCode());
        assertNotNull(createdBook.getBody());
        assertTrue(createdBook.getBody().path("id").isIntegralNumber());
        long bookId = createdBook.getBody().path("id").asLong();
        assertTrue(bookId > 0);
        assertEquals("/books/" + bookId, createdBook.getHeaders().getLocation().getPath());
        assertEquals(isbn, createdBook.getBody().path("isbn").asText());
        assertBookAvailability(bookId, true);

        ResponseEntity<JsonNode> createdMember = http.postForEntity("/members", Map.of(
                "name", "HTTP borrowing member", "email", email, "active", true), JsonNode.class);
        assertEquals(HttpStatus.CREATED, createdMember.getStatusCode());
        assertNotNull(createdMember.getBody());
        assertTrue(createdMember.getBody().path("id").isIntegralNumber());
        long memberId = createdMember.getBody().path("id").asLong();
        assertTrue(memberId > 0);
        assertEquals("/members/" + memberId, createdMember.getHeaders().getLocation().getPath());
        ResponseEntity<JsonNode> fetchedMember = http.getForEntity("/members/{id}", JsonNode.class, memberId);
        assertEquals(HttpStatus.OK, fetchedMember.getStatusCode());
        assertEquals(createdMember.getBody(), fetchedMember.getBody());
        assertEquals(email, fetchedMember.getBody().path("email").asText());
        assertEquals(TODAY.toString(), fetchedMember.getBody().path("membershipDate").asText());
        return new Resources(bookId, memberId);
    }

    private JsonNode borrow(Resources resources) {
        ResponseEntity<JsonNode> response = http.postForEntity("/borrowings",
                Map.of("bookId", resources.bookId(), "memberId", resources.memberId()), JsonNode.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        JsonNode loan = response.getBody();
        assertNotNull(loan);
        assertTrue(loan.path("id").isIntegralNumber());
        assertTrue(loan.path("id").asLong() > 0);
        assertEquals(resources.bookId(), loan.path("bookId").asLong());
        assertEquals(resources.memberId(), loan.path("memberId").asLong());
        assertEquals(TODAY.toString(), loan.path("borrowDate").asText());
        assertEquals(TODAY.plusDays(14).toString(), loan.path("dueDate").asText());
        assertTrue(loan.has("returnDate"));
        assertTrue(loan.path("returnDate").isNull());
        assertTrue(loan.path("fineAmount").isNumber());
        assertEquals(0, new BigDecimal("0.00").compareTo(loan.path("fineAmount").decimalValue()));
        return loan;
    }

    private void assertBookAvailability(long bookId, boolean expected) {
        ResponseEntity<JsonNode> response = http.getForEntity("/books/{id}", JsonNode.class, bookId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(bookId, response.getBody().path("id").asLong());
        assertTrue(response.getBody().path("available").isBoolean());
        assertEquals(expected, response.getBody().path("available").asBoolean());
        assertEquals(expected, books.findById(bookId).orElseThrow().isAvailable());
    }

    private record Resources(long bookId, long memberId) {
    }
}
