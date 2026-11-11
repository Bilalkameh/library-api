package com.library.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:openapidocumentationtest;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
class OpenApiDocumentationTest {
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "delete", "patch", "head", "options", "trace");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    private JsonNode document;

    @BeforeEach
    void readGeneratedDocument() throws Exception {
        String body = mvc.perform(get("/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        document = json.readTree(body);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    void documentsOperationContract(OperationContract contract) {
        JsonNode operation = document.path("paths").path(contract.path()).path(contract.method());
        assertTrue(operation.isObject(), contract + " is missing");
        assertFalse(operation.path("summary").asText().isBlank(), contract + " needs a summary");
        assertTrue(operation.path("tags").isArray() && !operation.path("tags").isEmpty(),
                contract + " needs a controller tag");
        operation.path("tags").forEach(tag -> assertFalse(tag.asText().isBlank()));

        JsonNode success = operation.path("responses").path(contract.successStatus());
        assertFalse(success.path("description").asText().isBlank(), contract + " success description");
        if (contract.responseSchema() == null) {
            assertTrue(success.path("content").isMissingNode() || success.path("content").isEmpty(),
                    "204 must not advertise a response body");
        } else {
            JsonNode schema = jsonContentSchema(success.path("content"), contract + " success");
            if (contract.collection()) {
                assertEquals("array", schema.path("type").asText(), contract + " returns a list");
                schema = schema.path("items");
            }
            assertSchemaReference(schema, contract.responseSchema(), contract + " success");
        }

        if (contract.requestSchema() != null) {
            JsonNode request = operation.path("requestBody");
            assertTrue(request.path("required").asBoolean(), contract + " requires a request body");
            assertSchemaReference(jsonContentSchema(request.path("content"), contract + " request"),
                    contract.requestSchema(), contract + " request");
        } else {
            assertTrue(operation.path("requestBody").isMissingNode(), contract + " has no request body");
        }

        Set<String> errorStatuses = new LinkedHashSet<>(contract.errors());
        errorStatuses.addAll(Set.of("406", "500"));
        for (String errorStatus : errorStatuses) {
            JsonNode error = operation.path("responses").path(errorStatus);
            assertFalse(error.path("description").asText().isBlank(), contract + " " + errorStatus);
            assertSchemaReference(error.path("content").path("application/problem+json").path("schema"),
                    "ApiError", contract + " " + errorStatus);
        }
    }

    @Test
    void documentsExactlyTheFourteenPublicOperations() {
        Set<String> actual = new LinkedHashSet<>();
        document.path("paths").fields().forEachRemaining(path ->
                path.getValue().fieldNames().forEachRemaining(method -> {
                    if (HTTP_METHODS.contains(method)) {
                        actual.add(method.toUpperCase() + " " + path.getKey());
                    }
                }));
        Set<String> expected = new LinkedHashSet<>();
        operations().forEach(contract -> expected.add(contract.toString()));
        assertEquals(14, expected.size());
        assertEquals(expected, actual);
    }

    @Test
    void dtoSchemasHaveFieldExamplesAndDoNotExposeJpaEntities() {
        Map<String, Set<String>> expectedFields = Map.of(
                "BookDTO", Set.of("id", "isbn", "title", "author", "genre", "publishedYear", "available"),
                "MemberDTO", Set.of("id", "name", "email", "membershipDate", "active"),
                "BorrowingDTO", Set.of("id", "bookId", "memberId", "borrowDate", "dueDate", "returnDate", "fineAmount"),
                "BorrowingRequest", Set.of("bookId", "memberId"),
                "ReservationRequest", Set.of("bookId", "memberId"),
                "ReservationResponse", Set.of("id", "bookId", "memberId", "reservationDate", "status", "cancelledDate"));

        expectedFields.forEach((schemaName, fields) -> {
            Map<String, JsonNode> properties = properties(schema(schemaName));
            assertEquals(fields, properties.keySet(), schemaName + " public fields");
            properties.forEach((field, property) -> {
                assertDocumentedField(schemaName + "." + field, property);
                JsonNode resolved = resolve(property);
                assertFalse(Set.of("object", "array").contains(resolved.path("type").asText()),
                        schemaName + "." + field + " must remain a flat scalar value");
            });
        });

        Map<String, JsonNode> errors = properties(schema("ApiError"));
        assertTrue(errors.keySet().containsAll(Set.of(
                "type", "title", "status", "detail", "instance", "error", "timestamp", "errors")),
                "The shared error schema must describe the compatible envelope and field errors");
        errors.forEach((field, property) -> assertDocumentedField("ApiError." + field, property));

        for (String entity : List.of("Book", "Member", "Borrowing", "Reservation")) {
            assertFalse(document.path("components").path("schemas").has(entity),
                    "Persistence entity " + entity + " must not become a public API schema");
        }
    }

    @Test
    void marksServerControlledFieldsReadOnlyAndDescribesRequestConstraints() {
        Map<String, Set<String>> serverFields = Map.of(
                "BookDTO", Set.of("id"),
                "MemberDTO", Set.of("id"),
                "BorrowingDTO", Set.of("id", "borrowDate", "dueDate", "returnDate", "fineAmount"),
                "ReservationResponse", Set.of("id", "reservationDate", "status", "cancelledDate"));
        serverFields.forEach((schemaName, fields) -> fields.forEach(field ->
                assertTrue(properties(schema(schemaName)).get(field).path("readOnly").asBoolean(),
                        schemaName + "." + field + " is server-controlled")));

        for (String requestName : List.of("BorrowingRequest", "ReservationRequest")) {
            JsonNode request = schema(requestName);
            Set<String> required = new LinkedHashSet<>();
            request.path("required").forEach(field -> required.add(field.asText()));
            assertTrue(required.containsAll(Set.of("bookId", "memberId")), requestName + " required IDs");
            for (String field : List.of("bookId", "memberId")) {
                JsonNode property = properties(request).get(field);
                assertEquals("integer", property.path("type").asText(), requestName + "." + field);
                assertEquals("int64", property.path("format").asText(), requestName + "." + field);
                assertPositiveMinimum(property, requestName + "." + field);
            }
        }
        assertPositiveMinimum(properties(schema("BookDTO")).get("publishedYear"), "BookDTO.publishedYear");
    }

    @Test
    void swaggerUiLoadsAndUsesTheConfiguredDocumentPath() throws Exception {
        String configurationBody = mvc.perform(get("/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        assertEquals("/api-docs", json.readTree(configurationBody).path("url").asText());

        String redirect = mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertNotNull(redirect);
        String html = mvc.perform(get(URI.create(redirect).getPath()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("swagger-ui"), "The redirect must load the Swagger UI resource");
    }

    private JsonNode schema(String name) {
        JsonNode schema = document.path("components").path("schemas").path(name);
        assertTrue(schema.isObject(), "Missing schema " + name);
        return schema;
    }

    private JsonNode resolve(JsonNode schema) {
        if (!schema.has("$ref")) {
            return schema;
        }
        String reference = schema.path("$ref").asText();
        assertTrue(reference.startsWith("#/"), "Only local schema references are expected");
        JsonNode resolved = document.at(reference.substring(1));
        assertFalse(resolved.isMissingNode(), "Unresolved reference " + reference);
        return resolved;
    }

    private Map<String, JsonNode> properties(JsonNode schema) {
        JsonNode resolved = resolve(schema);
        Map<String, JsonNode> properties = new LinkedHashMap<>();
        resolved.path("allOf").forEach(part -> properties.putAll(properties(part)));
        resolved.path("properties").fields().forEachRemaining(field ->
                properties.put(field.getKey(), field.getValue()));
        return properties;
    }

    private static JsonNode jsonContentSchema(JsonNode content, String context) {
        JsonNode media = content.has("application/json")
                ? content.path("application/json") : content.path("*/*");
        assertTrue(media.path("schema").isObject(), context + " needs a JSON-compatible schema");
        return media.path("schema");
    }

    private static void assertSchemaReference(JsonNode schema, String expected, String context) {
        assertEquals("#/components/schemas/" + expected, schema.path("$ref").asText(), context);
    }

    private static void assertDocumentedField(String name, JsonNode property) {
        assertFalse(property.path("description").asText().isBlank(), name + " needs a description");
        assertTrue(property.hasNonNull("example") || !property.path("examples").isEmpty(),
                name + " needs an example");
    }

    private static void assertPositiveMinimum(JsonNode property, String name) {
        JsonNode minimum = property.path("minimum");
        JsonNode exclusive = property.path("exclusiveMinimum");
        boolean positiveBound = minimum.isNumber()
                && (minimum.decimalValue().compareTo(BigDecimal.ZERO) > 0
                || minimum.decimalValue().compareTo(BigDecimal.ZERO) == 0 && exclusive.asBoolean());
        positiveBound |= exclusive.isNumber() && exclusive.decimalValue().compareTo(BigDecimal.ZERO) >= 0;
        assertTrue(positiveBound, name + " must document a strictly positive lower bound");
    }

    private static Stream<OperationContract> operations() {
        return Stream.of(
                new OperationContract("get", "/books", "200", "BookDTO", true, null, Set.of()),
                new OperationContract("post", "/books", "201", "BookDTO", false, "BookDTO", Set.of("400", "409", "415")),
                new OperationContract("get", "/books/{id}", "200", "BookDTO", false, null, Set.of("400", "404")),
                new OperationContract("put", "/books/{id}", "200", "BookDTO", false, "BookDTO", Set.of("400", "404", "409", "415")),
                new OperationContract("delete", "/books/{id}", "204", null, false, null, Set.of("400", "404", "409")),
                new OperationContract("get", "/members", "200", "MemberDTO", true, null, Set.of()),
                new OperationContract("post", "/members", "201", "MemberDTO", false, "MemberDTO", Set.of("400", "409", "415")),
                new OperationContract("get", "/members/{id}", "200", "MemberDTO", false, null, Set.of("400", "404")),
                new OperationContract("post", "/borrowings", "201", "BorrowingDTO", false, "BorrowingRequest", Set.of("400", "404", "409", "415")),
                new OperationContract("put", "/borrowings/{id}/return", "200", "BorrowingDTO", false, null, Set.of("400", "404", "409")),
                new OperationContract("get", "/members/{id}/borrowings", "200", "BorrowingDTO", true, null, Set.of("400", "404")),
                new OperationContract("post", "/reservations", "201", "ReservationResponse", false, "ReservationRequest", Set.of("400", "404", "409", "415")),
                new OperationContract("get", "/members/{memberId}/reservations", "200", "ReservationResponse", true, null, Set.of("400", "404")),
                new OperationContract("put", "/reservations/{id}/cancel", "200", "ReservationResponse", false, null, Set.of("400", "404", "409")));
    }

    private record OperationContract(String method, String path, String successStatus, String responseSchema,
                                     boolean collection, String requestSchema, Set<String> errors) {
        @Override
        public String toString() {
            return method.toUpperCase() + " " + path;
        }
    }
}
