package com.library.api.controller;

import com.library.api.dto.BorrowingRequest;
import com.library.api.dto.BorrowingResponse;
import com.library.api.service.BorrowingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BorrowingController {
    private final BorrowingService borrowingService;

    public BorrowingController(BorrowingService borrowingService) {
        this.borrowingService = borrowingService;
    }

    @PostMapping("/borrowings")
    public ResponseEntity<BorrowingResponse> borrow(@Valid @RequestBody BorrowingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BorrowingResponse.from(
                borrowingService.borrowBook(request.bookId(), request.memberId())));
    }

    @PutMapping("/borrowings/{id}/return")
    public BorrowingResponse returnBook(@PathVariable Long id) {
        return BorrowingResponse.from(borrowingService.returnBook(id));
    }

    @GetMapping("/members/{id}/borrowings")
    public List<BorrowingResponse> activeBorrowings(@PathVariable Long id) {
        return borrowingService.getActiveBorrowings(id).stream()
                .map(BorrowingResponse::from)
                .toList();
    }
}
