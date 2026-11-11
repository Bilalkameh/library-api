package com.library.api.service;

import com.library.api.entity.Book;
import com.library.api.entity.Member;
import com.library.api.entity.Reservation;
import com.library.api.entity.ReservationStatus;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.repository.BookRepository;
import com.library.api.repository.MemberRepository;
import com.library.api.repository.ReservationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
    private final BookRepository books;
    private final MemberRepository members;
    private final ReservationRepository reservations;
    private final Clock clock;

    public ReservationService(BookRepository books, MemberRepository members,
                              ReservationRepository reservations, Clock clock) {
        this.books = books;
        this.members = members;
        this.reservations = reservations;
        this.clock = clock;
    }

    @Transactional
    public Reservation reserveBook(Long bookId, Long memberId) {
        Book book = books.findByIdForUpdate(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId));
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
        if (book.isAvailable()) {
            throw new IllegalStateException("Book is available for direct borrowing.");
        }
        if (reservations.existsByBookIdAndMemberIdAndStatus(bookId, memberId, ReservationStatus.ACTIVE)) {
            throw new IllegalStateException("An active reservation already exists for this book and member.");
        }

        Reservation reservation = new Reservation();
        reservation.setBook(book);
        reservation.setMember(member);
        reservation.setReservationDate(LocalDate.now(clock));
        reservation.setStatus(ReservationStatus.ACTIVE);
        return reservations.save(reservation);
    }

    @Transactional
    public Reservation cancelReservation(Long reservationId) {
        Long bookId = reservations.findBookIdById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));
        books.findByIdForUpdate(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId));
        // Read the reservation state only after acquiring the lock shared by all its mutations.
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Reservation has already been cancelled.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledDate(LocalDate.now(clock));
        return reservations.save(reservation);
    }

    @Transactional(readOnly = true)
    public List<Reservation> getReservationsForMember(Long memberId) {
        if (!members.existsById(memberId)) {
            throw new ResourceNotFoundException("Member", memberId);
        }
        return reservations.findByMemberIdOrderByIdAsc(memberId);
    }
}
