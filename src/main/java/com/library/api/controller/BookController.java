package com.library.api.controller;

import com.library.api.dto.BookRequest;
import com.library.api.entity.Book;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/books")
public class BookController {
    private final BookRepository books;
    private final BorrowingRepository borrowings;

    public BookController(BookRepository books, BorrowingRepository borrowings) {
        this.books = books;
        this.borrowings = borrowings;
    }

    @GetMapping
    public List<Book> getAll() {
        return books.findAll(Sort.by("id"));
    }

    @GetMapping("/{id}")
    public Book getById(@PathVariable Long id) {
        return books.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));
    }

    @PostMapping
    public ResponseEntity<Book> create(@Valid @RequestBody BookRequest request) {
        Book book = new Book();
        apply(request, book);
        Book saved = books.saveAndFlush(book);
        return ResponseEntity.created(URI.create("/books/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public Book update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        Book book = books.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));
        if ((request.available() == null || request.available())
                && borrowings.existsByBookIdAndReturnDateIsNull(id)) {
            throw new IllegalStateException("A book with an active borrowing cannot be made available.");
        }
        apply(request, book);
        return books.saveAndFlush(book);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Book book = getById(id);
        books.delete(book);
        books.flush();
        return ResponseEntity.noContent().build();
    }

    private void apply(BookRequest request, Book book) {
        book.setIsbn(request.isbn());
        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setGenre(request.genre());
        book.setPublishedYear(request.publishedYear());
        book.setAvailable(request.available() == null || request.available());
    }
}
