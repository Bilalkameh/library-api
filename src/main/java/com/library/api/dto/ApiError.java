package com.library.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Consistent error envelope retaining the standard ProblemDetail fields.")
public class ApiError extends ProblemDetail {
    public ApiError(HttpStatusCode status, String message, Instant timestamp) {
        super(ProblemDetail.forStatusAndDetail(status, message));
        super.setProperty("timestamp", timestamp);
    }

    @Schema(description = "Public error message, identical to detail", example = "Book with id 99 was not found")
    public String getError() {
        return getDetail();
    }

    @Schema(description = "UTC instant when the error response was created", example = "2026-11-11T18:30:00Z")
    public Instant getTimestamp() {
        return (Instant) extension("timestamp");
    }

    @Schema(description = "Validation messages keyed by request field; present for field validation errors",
            example = "{\"bookId\":\"must be greater than 0\"}")
    @SuppressWarnings("unchecked")
    public Map<String, String> getErrors() {
        return (Map<String, String>) extension("errors");
    }

    public void setErrors(Map<String, String> errors) {
        super.setProperty("errors", Map.copyOf(errors));
    }

    @Override
    @Schema(description = "Problem type URI", example = "about:blank")
    public URI getType() {
        return super.getType();
    }

    @Override
    @Schema(description = "HTTP status description", example = "Not Found")
    public String getTitle() {
        return super.getTitle();
    }

    @Override
    @Schema(description = "HTTP status code", example = "404")
    public int getStatus() {
        return super.getStatus();
    }

    @Override
    @Schema(description = "Public error message", example = "Book with id 99 was not found")
    public String getDetail() {
        return super.getDetail();
    }

    @Override
    @Schema(description = "Request path", example = "/books/99")
    public URI getInstance() {
        return super.getInstance();
    }

    @Override
    @Schema(hidden = true)
    @JsonIgnoreProperties({"timestamp", "errors"})
    public Map<String, Object> getProperties() {
        return super.getProperties();
    }

    // These reserved extension keys hold Instant and Map<String, String> values.
    // Keeping one copy in ProblemDetail preserves its symmetric equals/hashCode contract.
    private Object extension(String name) {
        Map<String, Object> properties = super.getProperties();
        return properties == null ? null : properties.get(name);
    }
}
