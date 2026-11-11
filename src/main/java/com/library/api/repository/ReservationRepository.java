package com.library.api.repository;

import com.library.api.entity.Reservation;
import com.library.api.entity.ReservationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    boolean existsByBookIdAndMemberIdAndStatus(Long bookId, Long memberId, ReservationStatus status);

    List<Reservation> findByMemberIdOrderByIdAsc(Long memberId);

    @Query("select r.book.id from Reservation r where r.id = :id")
    Optional<Long> findBookIdById(@Param("id") Long id);
}
