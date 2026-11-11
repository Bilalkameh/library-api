package com.library.api.mapper;

import com.library.api.dto.BookDTO;
import com.library.api.entity.Book;
import org.springframework.stereotype.Component;

@Component
public class BookMapper {
    public BookDTO toDTO(Book book) {
        return new BookDTO(book.getId(), book.getIsbn(), book.getTitle(), book.getAuthor(),
                book.getGenre(), book.getPublishedYear(), book.isAvailable());
    }

    public Book toEntity(BookDTO dto) {
        Book book = new Book();
        book.setId(dto.id());
        updateEntity(dto, book);
        return book;
    }

    public void updateEntity(BookDTO dto, Book book) {
        book.setIsbn(dto.isbn());
        book.setTitle(dto.title());
        book.setAuthor(dto.author());
        book.setGenre(dto.genre());
        book.setPublishedYear(dto.publishedYear());
        if (dto.available() != null) {
            book.setAvailable(dto.available());
        }
    }
}
