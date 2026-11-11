package com.library.api.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.api.entity.Book;
import com.library.api.entity.Member;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import com.library.api.support.FixedClockConfiguration;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:exceptionhandlertest;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
@Import({FixedClockConfiguration.class, GlobalExceptionHandlerTest.ThrowingController.class})
class GlobalExceptionHandlerTest {
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 29);
    private static final String EXPECTED_TIMESTAMP = "2024-02-28T22:30:00Z";
    private static final String INTERNAL_MARKER = "private-internal-diagnostic-marker";

    @Autowired private org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private BookRepository books;
    @Autowired private MemberRepository members;
    @Autowired private BorrowingRepository borrowings;

    private Book book;
    private Member member;

    @BeforeEach
    void setUp() {
        book = new Book();
        book.setIsbn("errors-" + UUID.randomUUID().toString().substring(0, 20));
        book.setTitle("Exception response fixture");
        book.setAuthor("Test Author");
        book.setGenre("Engineering");
        book.setPublishedYear(2024);
        book.setAvailable(true);
        book = books.saveAndFlush(book);

        member = new Member();
        member.setName("Exception test member");
        member.setEmail(UUID.randomUUID() + "@example.com");
        member.setMembershipDate(TODAY);
        member.setActive(true);
        member = members.saveAndFlush(member);
    }

    @AfterEach
    void tearDown() {
        borrowings.deleteAll();
        if (book != null && book.getId() != null) {
            books.deleteById(book.getId());
        }
        if (member != null && member.getId() != null) {
            members.deleteById(member.getId());
        }
    }

    @Test
    void missingBook_returns404WithCompatibleErrorEnvelope() throws Exception {
        assertProblem(mvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(borrowingBody(Long.MAX_VALUE))),
                404, "Book with id " + Long.MAX_VALUE + " was not found");

        assertEquals(0, borrowings.count());
        assertTrue(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void alreadyBorrowedBook_returns409WithoutAnotherLoan() throws Exception {
        mvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(borrowingBody(book.getId())))
                .andExpect(status().isCreated());
        long countAfterFirstLoan = borrowings.count();

        assertProblem(mvc.perform(post("/borrowings").contentType(MediaType.APPLICATION_JSON)
                        .content(borrowingBody(book.getId()))),
                409, "Book is already borrowed.");

        assertEquals(countAfterFirstLoan, borrowings.count());
        assertFalse(books.findById(book.getId()).orElseThrow().isAvailable());
    }

    @Test
    void invalidBody_returns400AndPreservesFieldErrors() throws Exception {
        JsonNode error = assertProblem(mvc.perform(post("/borrowings")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")),
                400, "Request validation failed");

        assertTrue(error.path("errors").hasNonNull("bookId"));
        assertTrue(error.path("errors").hasNonNull("memberId"));
        assertEquals(0, borrowings.count());
    }

    @ParameterizedTest
    @CsvSource({
            "entity, Requested test entity was not found.",
            "element, Requested test element was not found."
    })
    void standardMissingResourceExceptions_return404(String route, String expectedDetail) throws Exception {
        assertProblem(mvc.perform(get("/__test/errors/" + route)), 404, expectedDetail);
    }

    @Test
    void unexpectedException_returns500WithoutExposingInternals() throws Exception {
        JsonNode error = assertProblem(mvc.perform(get("/__test/errors/unexpected")),
                500, "An unexpected error occurred.");

        assertFalse(error.toString().contains(INTERNAL_MARKER));
        assertFalse(error.has("stackTrace"));
        assertFalse(error.has("exception"));
    }

    @Test
    void unsupportedMethod_keeps405AndAllowHeader() throws Exception {
        ResultActions result = mvc.perform(delete("/members"))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")));

        assertProblem(result, 405, null);
    }

    @Test
    void unsupportedRequestMediaType_keeps415() throws Exception {
        assertProblem(mvc.perform(post("/borrowings").contentType(MediaType.TEXT_PLAIN)
                        .content("bookId=1&memberId=1")),
                415, null);
    }

    @Test
    void unknownPath_keeps404() throws Exception {
        assertProblem(mvc.perform(get("/__test/no-such-endpoint")), 404, null);
    }

    @Test
    void unacceptableResponseType_keeps406() throws Exception {
        assertProblem(mvc.perform(get("/__test/errors/json-only").accept(MediaType.APPLICATION_PDF)),
                406, null);
    }

    private JsonNode assertProblem(ResultActions result, int expectedStatus, String expectedDetail)
            throws Exception {
        String body = result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        JsonNode error = json.readTree(body);
        assertEquals(expectedStatus, error.path("status").asInt());
        assertTrue(error.hasNonNull("detail"));
        assertTrue(error.hasNonNull("error"));
        assertFalse(error.get("detail").asText().isBlank());
        assertEquals(error.get("detail").asText(), error.get("error").asText());
        assertEquals(EXPECTED_TIMESTAMP, error.path("timestamp").asText());
        assertEquals(Instant.parse(EXPECTED_TIMESTAMP), Instant.parse(error.get("timestamp").asText()));
        if (expectedDetail != null) {
            assertEquals(expectedDetail, error.get("detail").asText());
        }
        return error;
    }

    private String borrowingBody(Long bookId) throws Exception {
        return json.writeValueAsString(Map.of("bookId", bookId, "memberId", member.getId()));
    }

    @TestComponent
    @RestController
    @Hidden
    @RequestMapping("/__test/errors")
    static class ThrowingController {
        @GetMapping("/entity")
        public void missingEntity() {
            throw new EntityNotFoundException("Requested test entity was not found.");
        }

        @GetMapping("/element")
        public void missingElement() {
            throw new NoSuchElementException("Requested test element was not found.");
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new RuntimeException(INTERNAL_MARKER);
        }

        @GetMapping(value = "/json-only", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> jsonOnly() {
            return Map.of("value", "test");
        }
    }
}
