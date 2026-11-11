package com.library.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Consistent error envelope retaining the standard ProblemDetail fields.")
public class ApiError extends ProblemDetail {
    private final Instant timestamp;
    private Map<String, String> errors;

    public ApiError(HttpStatusCode status, String message, Instant timestamp) {
        super(ProblemDetail.forStatusAndDetail(status, message));
        this.timestamp = timestamp;
    }

    @Schema(description = "Public error message, identical to detail", example = "Book with id 99 was not found")
    public String getError() {
        return getDetail();
    }

    @Schema(description = "UTC instant when the error response was created", example = "2026-11-11T18:30:00Z")
    public Instant getTimestamp() {
        return timestamp;
    }

    @Schema(description = "Validation messages keyed by request field; present for field validation errors",
            example = "{\"bookId\":\"must be greater than 0\"}")
    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = Map.copyOf(errors);
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
    public Map<String, Object> getProperties() {
        return super.getProperties();
    }
}
