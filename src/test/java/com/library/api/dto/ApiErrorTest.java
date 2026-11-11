package com.library.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiErrorTest {
    private static final Instant TIME = Instant.parse("2026-11-11T12:00:00Z");
    private static final String MESSAGE = "Request validation failed";
    private static final Map<String, String> FIELD_ERRORS = Map.of("bookId", "must be positive");

    @Test
    void differentTimestampsDistinguishErrorOccurrences() {
        ApiError first = new ApiError(HttpStatus.BAD_REQUEST, MESSAGE, TIME);
        ApiError second = new ApiError(HttpStatus.BAD_REQUEST, MESSAGE, TIME.plusSeconds(1));

        assertNotEquals(first, second);
        assertNotEquals(second, first);
    }

    @Test
    void differentValidationFieldsDistinguishErrors() {
        ApiError first = validationError();
        ApiError second = validationError();
        second.setErrors(Map.of("memberId", "must be positive"));

        assertNotEquals(first, second);
        assertNotEquals(second, first);
    }

    @Test
    void equalValuesHaveEqualHashesAndDeduplicateInASet() {
        ApiError first = validationError();
        ApiError second = validationError();
        ApiError third = validationError();

        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(second, third);
        assertEquals(first, third);
        assertEquals(first.hashCode(), second.hashCode());
        Set<ProblemDetail> errors = new HashSet<>();
        errors.add(first);
        errors.add(second);
        assertEquals(1, errors.size());
        assertFalse(first.equals(null));
        assertFalse(first.equals("error"));
    }

    @Test
    void plainProblemDetailAndApiErrorHaveSymmetricValueEquality() {
        ApiError error = validationError();
        ProblemDetail same = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, MESSAGE);
        same.setProperty("timestamp", TIME);
        same.setProperty("errors", FIELD_ERRORS);
        ProblemDetail withoutExtensions = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, MESSAGE);

        assertEquals(same, error);
        assertEquals(error, same);
        assertEquals(same.hashCode(), error.hashCode());
        assertNotEquals(withoutExtensions, error);
        assertNotEquals(error, withoutExtensions);
    }

    @ParameterizedTest(name = "changing {0} still changes the problem value")
    @MethodSource("baseFieldChanges")
    void inheritedProblemFieldsRemainPartOfEquality(String field, Consumer<ApiError> change) {
        ApiError first = validationError();
        ApiError second = validationError();
        change.accept(second);

        assertNotEquals(first, second, field);
        assertNotEquals(second, first, field);
    }

    private static Stream<Arguments> baseFieldChanges() {
        return Stream.of(
                Arguments.of("type", (Consumer<ApiError>) error -> error.setType(URI.create("urn:problem:validation"))),
                Arguments.of("title", (Consumer<ApiError>) error -> error.setTitle("Invalid identifiers")),
                Arguments.of("status", (Consumer<ApiError>) error -> error.setStatus(HttpStatus.CONFLICT)),
                Arguments.of("detail", (Consumer<ApiError>) error -> error.setDetail("Different detail")),
                Arguments.of("instance", (Consumer<ApiError>) error -> error.setInstance(URI.create("/books/99"))),
                Arguments.of("extension", (Consumer<ApiError>) error -> error.setProperty("traceId", "trace-1")));
    }

    @Test
    void validationErrorsRemainDefensivelyCopiedAndReadOnly() {
        Map<String, String> fields = new HashMap<>(FIELD_ERRORS);
        ApiError error = new ApiError(HttpStatus.BAD_REQUEST, MESSAGE, TIME);
        error.setErrors(fields);
        fields.put("bookId", "changed after assignment");

        assertEquals(FIELD_ERRORS, error.getErrors());
        assertThrows(UnsupportedOperationException.class, () -> error.getErrors().put("other", "error"));
    }

    @Test
    void serializesEachExtensionOnceAndKeepsOtherExtensionsFlat() throws Exception {
        ApiError error = validationError();
        error.setProperty("traceId", "trace-1");
        ObjectMapper mapper = jsonMapper();

        String serialized = mapper.writeValueAsString(error);
        JsonNode body = mapper.readTree(serialized);

        assertEquals(8, body.size());
        assertEquals(400, body.path("status").asInt());
        assertEquals("about:blank", body.path("type").asText());
        assertEquals("Bad Request", body.path("title").asText());
        assertEquals(MESSAGE, body.path("detail").asText());
        assertEquals(MESSAGE, body.path("error").asText());
        assertEquals(TIME.toString(), body.path("timestamp").asText());
        assertEquals("must be positive", body.path("errors").path("bookId").asText());
        assertEquals("trace-1", body.path("traceId").asText());
        assertFalse(body.has("properties"));
    }

    @Test
    void omitsUnsetValidationFieldsFromTheWireFormat() throws Exception {
        ApiError error = new ApiError(HttpStatus.NOT_FOUND, "Book not found", TIME);
        ObjectMapper mapper = jsonMapper();

        assertNull(error.getErrors());
        assertFalse(mapper.readTree(mapper.writeValueAsString(error)).has("errors"));
    }

    @Test
    void typedGettersReflectReplacementAndClearingOfInheritedProperties() {
        ApiError error = validationError();
        Instant updated = TIME.plusSeconds(2);
        Map<String, String> fields = Map.of("memberId", "must be positive");
        error.setProperties(Map.of("timestamp", updated, "errors", fields));

        assertEquals(updated, error.getTimestamp());
        assertEquals(fields, error.getErrors());
        error.setProperties(null);
        assertNull(error.getTimestamp());
        assertNull(error.getErrors());
    }

    private static ApiError validationError() {
        ApiError error = new ApiError(HttpStatus.BAD_REQUEST, MESSAGE, TIME);
        error.setErrors(FIELD_ERRORS);
        return error;
    }

    private static ObjectMapper jsonMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
    }
}
