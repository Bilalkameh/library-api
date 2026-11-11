package com.library.api.controller;

import com.library.api.dto.ApiError;
import com.library.api.dto.BookDTO;
import com.library.api.mapper.BookMapper;
import com.library.api.service.BookService;
import java.net.URI;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/books")
@Tag(name = "Books", description = "Book catalogue and availability management")
@ApiResponse(responseCode = "500", description = "Unexpected server error",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "406", description = "Requested response media type is not supported",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
public class BookController {
    private final BookService books;
    private final BookMapper mapper;

    public BookController(BookService books, BookMapper mapper) {
        this.books = books;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List books in ascending ID order")
    @ApiResponse(responseCode = "200", description = "Books, including an empty list")
    public List<BookDTO> getAll() {
        return books.getAll().stream().map(mapper::toDTO).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a book by ID")
    @ApiResponse(responseCode = "200", description = "Book details")
    @ApiResponse(responseCode = "400", description = "Malformed book ID",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Book not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public BookDTO getById(@PathVariable Long id) {
        return mapper.toDTO(books.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create a book", description = "Returns a generated ID and a Location header. Omitted availability defaults to true.")
    @ApiResponse(responseCode = "201", description = "Created book; Location identifies /books/{id}")
    @ApiResponse(responseCode = "400", description = "Invalid book fields or malformed JSON",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "ISBN already exists",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Request media type is not supported",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<BookDTO> create(@Valid @RequestBody BookDTO request) {
        BookDTO saved = mapper.toDTO(books.create(request));
        return ResponseEntity.created(URI.create("/books/" + saved.id())).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a book's editable details", description = "Omitted availability defaults to true. An active loan requires available=false until returned. The path ID is authoritative.")
    @ApiResponse(responseCode = "200", description = "Updated book")
    @ApiResponse(responseCode = "400", description = "Invalid book ID, fields, or JSON",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Book not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Duplicate ISBN or an active loan prevents availability",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Request media type is not supported",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public BookDTO update(@PathVariable Long id, @Valid @RequestBody BookDTO request) {
        return mapper.toDTO(books.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an unreferenced book", description = "Borrowing or reservation history prevents deletion through foreign-key constraints.")
    @ApiResponse(responseCode = "204", description = "Book deleted; no response body", content = @Content)
    @ApiResponse(responseCode = "400", description = "Malformed book ID",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Book not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Book is referenced by existing records",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        books.delete(id);
        return ResponseEntity.noContent().build();
    }
}
