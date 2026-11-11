package com.library.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.api.dto.BookDTO;
import com.library.api.dto.MemberDTO;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import com.library.api.repository.MemberRepository;
import com.library.api.repository.ReservationRepository;
import com.library.api.service.BookService;
import com.library.api.service.MemberService;
import com.library.api.support.FixedClockConfiguration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cataloguecontracttest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
class CatalogueContractTest {
    private static final LocalDate TODAY = LocalDate.of(2024, 2, 29);
    private static final Set<String> BOOK_FIELDS = Set.of("id", "isbn", "title", "author", "genre", "publishedYear", "available");
    private static final Set<String> MEMBER_FIELDS = Set.of("id", "name", "email", "membershipDate", "active");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private BookRepository books;
    @Autowired private MemberRepository members;
    @Autowired private BorrowingRepository borrowings;
    @Autowired private ReservationRepository reservations;
    @Autowired private BookService bookService;
    @Autowired private MemberService memberService;

    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void cleanFixtures() {
        reservations.deleteAll();
        borrowings.deleteAll();
        bookIds.forEach(id -> {
            if (books.existsById(id)) books.deleteById(id);
        });
        memberIds.forEach(members::deleteById);
    }

    @Test
    void creatingBookIgnoresSuppliedIdentityAndPreservesResponseContract() throws Exception {
        String seedTitle = books.findById(1L).orElseThrow().getTitle();
        Map<String, Object> request = bookBody();
        request.put("id", 1);
        JsonNode created = createBook(request);

        assertNotEquals(1L, created.path("id").asLong());
        assertEquals(BOOK_FIELDS, fields(created));
        assertTrue(created.path("available").asBoolean());
        assertEquals(seedTitle, books.findById(1L).orElseThrow().getTitle());
        assertEquals(request.get("isbn"), books.findById(created.path("id").asLong()).orElseThrow().getIsbn());
        String fetched = mvc.perform(get("/books/{id}", created.path("id").asLong()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(created, json.readTree(fetched));
    }

    @Test
    void replacementUsesPathIdentityAndRetainsOmittedAvailabilityDefault() throws Exception {
        Map<String, Object> original = bookBody();
        original.put("available", false);
        long id = createBook(original).path("id").asLong();
        Map<String, Object> replacement = bookBody();
        replacement.put("id", 1);
        replacement.put("title", "Replaced title");

        String response = mvc.perform(put("/books/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(replacement)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.available").value(true)).andReturn().getResponse().getContentAsString();

        assertEquals(BOOK_FIELDS, fields(json.readTree(response)));
        assertEquals("Replaced title", books.findById(id).orElseThrow().getTitle());
        assertNotEquals("Replaced title", books.findById(1L).orElseThrow().getTitle());
    }

    @Test
    void memberCreationUsesFixedLocalDateAndIgnoresSuppliedIdentity() throws Exception {
        String seedName = members.findById(1L).orElseThrow().getName();
        Map<String, Object> request = memberBody();
        request.put("id", 1);
        JsonNode created = createMember(request);

        assertNotEquals(1L, created.path("id").asLong());
        assertEquals(MEMBER_FIELDS, fields(created));
        assertEquals(TODAY.toString(), created.path("membershipDate").asText());
        assertTrue(created.path("active").asBoolean());
        assertEquals(TODAY, members.findById(created.path("id").asLong()).orElseThrow().getMembershipDate());
        assertEquals(seedName, members.findById(1L).orElseThrow().getName());
    }

    @Test
    void explicitFalseFlagsAndMembershipDateSurviveMapping() throws Exception {
        Map<String, Object> bookRequest = bookBody();
        bookRequest.put("available", false);
        JsonNode book = createBook(bookRequest);
        Map<String, Object> memberRequest = memberBody();
        memberRequest.put("active", false);
        memberRequest.put("membershipDate", "2020-01-15");
        JsonNode member = createMember(memberRequest);

        assertFalse(book.path("available").asBoolean());
        assertFalse(books.findById(book.path("id").asLong()).orElseThrow().isAvailable());
        assertFalse(member.path("active").asBoolean());
        assertEquals("2020-01-15", member.path("membershipDate").asText());
        assertFalse(members.findById(member.path("id").asLong()).orElseThrow().isActive());
    }

    @Test
    void requestValidationStillRejectsInvalidCatalogueValues() throws Exception {
        long bookCount = books.count();
        long memberCount = members.count();
        mvc.perform(post("/books").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.isbn").exists())
                .andExpect(jsonPath("$.errors.publishedYear").exists());
        mvc.perform(post("/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists());
        Map<String, Object> fractional = bookBody();
        fractional.put("publishedYear", 2024.5);
        mvc.perform(post("/books").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fractional)))
                .andExpect(status().isBadRequest());
        assertEquals(bookCount, books.count());
        assertEquals(memberCount, members.count());
    }

    @Test
    void uniqueIdentifiersStillProduceConflictsWithoutExtraRows() throws Exception {
        Map<String, Object> book = bookBody();
        Map<String, Object> member = memberBody();
        createBook(book);
        createMember(member);
        long bookCount = books.count();
        long memberCount = members.count();

        mvc.perform(post("/books").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(book)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").exists());
        mvc.perform(post("/members").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(member)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").exists());
        assertEquals(bookCount, books.count());
        assertEquals(memberCount, members.count());
    }

    @Test
    void deletionRemainsBodylessAndReferencedBooksRemainProtected() throws Exception {
        long removable = createBook(bookBody()).path("id").asLong();
        mvc.perform(delete("/books/{id}", removable)).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(get("/books/{id}", removable)).andExpect(status().isNotFound());

        Map<String, Object> unavailable = bookBody();
        unavailable.put("available", false);
        long referenced = createBook(unavailable).path("id").asLong();
        long member = createMember(memberBody()).path("id").asLong();
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("bookId", referenced, "memberId", member))))
                .andExpect(status().isCreated());
        mvc.perform(delete("/books/{id}", referenced)).andExpect(status().isConflict());
        assertTrue(books.existsById(referenced));
        assertEquals(1, reservations.count());
    }

    @Test
    void directoryListsRetainOrderingAndExactTransportFields() throws Exception {
        createBook(bookBody());
        createMember(memberBody());
        for (String path : List.of("/books", "/members")) {
            JsonNode list = json.readTree(mvc.perform(get(path)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            long previous = 0;
            for (JsonNode record : list) {
                assertTrue(record.path("id").asLong() > previous);
                previous = record.path("id").asLong();
                assertEquals(path.equals("/books") ? BOOK_FIELDS : MEMBER_FIELDS, fields(record));
            }
        }
    }

    @Test
    void servicesProtectGeneratedIdentityForNonHttpCallers() {
        var book = bookService.create(new BookDTO(1L, uniqueIsbn(), "Service book", "Test Author",
                "Engineering", 2024, null));
        bookIds.add(book.getId());
        var member = memberService.create(new MemberDTO(1L, "Service member", UUID.randomUUID() + "@example.com",
                null, null));
        memberIds.add(member.getId());

        assertNotEquals(1L, book.getId());
        assertNotEquals(1L, member.getId());
        assertTrue(book.isAvailable());
        assertTrue(member.isActive());
        assertEquals(TODAY, member.getMembershipDate());
    }

    private JsonNode createBook(Map<String, Object> body) throws Exception {
        var response = mvc.perform(post("/books").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        JsonNode saved = json.readTree(response.getContentAsString());
        bookIds.add(saved.path("id").asLong());
        assertEquals("/books/" + saved.path("id").asLong(), response.getHeader("Location"));
        return saved;
    }

    private JsonNode createMember(Map<String, Object> body) throws Exception {
        var response = mvc.perform(post("/members").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        JsonNode saved = json.readTree(response.getContentAsString());
        memberIds.add(saved.path("id").asLong());
        assertEquals("/members/" + saved.path("id").asLong(), response.getHeader("Location"));
        return saved;
    }

    private Map<String, Object> bookBody() {
        return new LinkedHashMap<>(Map.of("isbn", uniqueIsbn(), "title", "Catalogue contract",
                "author", "Test Author", "genre", "Engineering", "publishedYear", 2024));
    }

    private Map<String, Object> memberBody() {
        return new LinkedHashMap<>(Map.of("name", "Catalogue member", "email", UUID.randomUUID() + "@example.com"));
    }

    private String uniqueIsbn() {
        return "lab4-" + UUID.randomUUID().toString().substring(0, 20);
    }

    private Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
