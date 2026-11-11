package com.library.api.mapper;

import com.library.api.dto.BorrowingDTO;
import com.library.api.entity.Book;
import com.library.api.entity.Borrowing;
import com.library.api.entity.Member;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class BorrowingMapper {
    public BorrowingDTO toDTO(Borrowing borrowing) {
        return new BorrowingDTO(borrowing.getId(), borrowing.getBook().getId(),
                borrowing.getMember().getId(), borrowing.getBorrowDate(), borrowing.getDueDate(),
                borrowing.getReturnDate(), borrowing.getFineAmount());
    }

    /** Maps an internal DTO using associations already resolved by the caller; performs no persistence. */
    public Borrowing toEntity(BorrowingDTO dto, Book book, Member member) {
        if (!Objects.equals(dto.bookId(), book.getId()) || !Objects.equals(dto.memberId(), member.getId())) {
            throw new IllegalArgumentException("Resolved associations must match the borrowing DTO.");
        }
        Borrowing borrowing = new Borrowing();
        borrowing.setId(dto.id());
        borrowing.setBook(book);
        borrowing.setMember(member);
        borrowing.setBorrowDate(dto.borrowDate());
        borrowing.setDueDate(dto.dueDate());
        borrowing.setReturnDate(dto.returnDate());
        borrowing.setFineAmount(dto.fineAmount());
        return borrowing;
    }
}
