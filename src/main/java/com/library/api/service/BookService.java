package com.library.api.service;

import com.library.api.dto.BookDTO;
import com.library.api.entity.Book;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.mapper.BookMapper;
import com.library.api.repository.BookRepository;
import com.library.api.repository.BorrowingRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {
    private final BookRepository books;
    private final BorrowingRepository borrowings;
    private final BookMapper mapper;

    public BookService(BookRepository books, BorrowingRepository borrowings, BookMapper mapper) {
        this.books = books;
        this.borrowings = borrowings;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Book> getAll() {
        return books.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Book getById(Long id) {
        return books.findById(id).orElseThrow(() -> new ResourceNotFoundException("Book", id));
    }

    @Transactional
    public Book create(BookDTO dto) {
        Book book = mapper.toEntity(dto);
        book.setId(null);
        book.setAvailable(dto.available() == null || dto.available());
        return books.saveAndFlush(book);
    }

    @Transactional
    public Book update(Long id, BookDTO dto) {
        Book book = books.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));
        boolean available = dto.available() == null || dto.available();
        if (available && borrowings.existsByBookIdAndReturnDateIsNull(id)) {
            throw new IllegalStateException("A book with an active borrowing cannot be made available.");
        }
        mapper.updateEntity(dto, book);
        book.setAvailable(available);
        return books.saveAndFlush(book);
    }

    @Transactional
    public void delete(Long id) {
        Book book = getById(id);
        books.delete(book);
        books.flush();
    }
}
