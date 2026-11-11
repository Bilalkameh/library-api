package com.library.api.repository;

import com.library.api.entity.Borrowing;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowingRepository extends JpaRepository<Borrowing, Long> {
    boolean existsByBookIdAndReturnDateIsNull(Long bookId);

    List<Borrowing> findByMemberIdAndReturnDateIsNullOrderByIdAsc(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Borrowing b where b.id = :id")
    Optional<Borrowing> findByIdForUpdate(@Param("id") Long id);
}
