package com.library.api.exception;

import com.library.api.dto.ApiError;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ApiError illegalState(IllegalStateException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({ResourceNotFoundException.class, EntityNotFoundException.class, NoSuchElementException.class})
    public ApiError notFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ApiError conflict(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT,
                "The request conflicts with existing data. ISBN and email must be unique, "
                        + "and referenced records cannot be deleted.");
    }

    @ExceptionHandler(Exception.class)
    public ApiError unexpected(Exception exception) {
        logger.error("Unexpected request failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ApiError body = problem(status, "Request validation failed");
        body.setErrors(errors);
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception, problem(status, "Malformed request body or invalid parameter type"),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception, problem(status, "Malformed request body or invalid parameter type"),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (body instanceof ApiError) {
            return super.createResponseEntity(body, headers, status, request);
        }
        String detail = body instanceof ProblemDetail problem ? problem.getDetail() : "Request could not be processed.";
        if (status.is5xxServerError()) {
            detail = "An unexpected error occurred.";
        }
        return super.createResponseEntity(problem(status, detail), headers, status, request);
    }

    private ApiError problem(HttpStatusCode status, String detail) {
        return new ApiError(status, detail, Instant.now(clock));
    }
}
