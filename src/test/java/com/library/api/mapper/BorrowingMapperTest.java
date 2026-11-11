package com.library.api.mapper;

import com.library.api.dto.BorrowingDTO;
import com.library.api.entity.Book;
import com.library.api.entity.Member;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BorrowingMapperTest {
    private final BorrowingMapper mapper = new BorrowingMapper();

    @Test
    void reverseMappingReusesResolvedAssociationsAndPreservesLoanState() {
        Book book = new Book();
        book.setId(6L);
        Member member = new Member();
        member.setId(4L);
        BorrowingDTO dto = new BorrowingDTO(10L, 6L, 4L, LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 15), LocalDate.of(2024, 2, 18), new BigDecimal("1.50"));

        var entity = mapper.toEntity(dto, book, member);

        assertSame(book, entity.getBook());
        assertSame(member, entity.getMember());
        assertEquals(dto, mapper.toDTO(entity));
        assertTrue(book.isAvailable());
    }

    @Test
    void reverseMappingRejectsAnAssociationForAnotherRecord() {
        Book book = new Book();
        book.setId(7L);
        Member member = new Member();
        member.setId(4L);
        BorrowingDTO dto = new BorrowingDTO(10L, 6L, 4L, LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 15), null, new BigDecimal("0.00"));

        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(dto, book, member));
        book.setId(6L);
        member.setId(5L);
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(dto, book, member));
    }
}
