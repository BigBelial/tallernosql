package com.noSQL.claseJava.controllers;

import com.noSQL.claseJava.dto.LoanRequest;
import com.noSQL.claseJava.dto.LoanStatsResponse;
import com.noSQL.claseJava.entity.Loan;
import com.noSQL.claseJava.entity.LoanStatus;
import com.noSQL.claseJava.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    // Prestar un libro
    @PostMapping
    public ResponseEntity<Loan> createLoan(@Valid @RequestBody LoanRequest request) {
        Loan createdLoan = loanService.createLoan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdLoan);
    }

    // Devolver un libro
    @PutMapping("/{id}/return")
    public ResponseEntity<Loan> returnLoan(@PathVariable String id) {
        return ResponseEntity.ok(loanService.returnLoan(id));
    }

    @GetMapping
    public ResponseEntity<List<Loan>> getAllLoans() {
        return ResponseEntity.ok(loanService.getAllLoans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Loan> getLoanById(@PathVariable String id) {
        Optional<Loan> loan = loanService.getLoanById(id);
        return loan.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Préstamos de un usuario. Opcional: ?status=ACTIVE o ?status=RETURNED
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Loan>> getLoansByUser(@PathVariable String userId,
                                                     @RequestParam(required = false) LoanStatus status) {
        if (status != null) {
            return ResponseEntity.ok(loanService.getLoansByUserAndStatus(userId, status));
        }
        return ResponseEntity.ok(loanService.getLoansByUser(userId));
    }

    @GetMapping("/user/{userId}/late")
    public ResponseEntity<List<Loan>> getLateReturnsByUser(@PathVariable String userId) {
        return ResponseEntity.ok(loanService.getLateReturnsByUser(userId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Loan>> getLoansByStatus(@PathVariable LoanStatus status) {
        return ResponseEntity.ok(loanService.getLoansByStatus(status));
    }

    // Historial de préstamos de un libro
    @GetMapping("/book/{bookId}")
    public ResponseEntity<List<Loan>> getLoansByBook(@PathVariable String bookId) {
        return ResponseEntity.ok(loanService.getLoansByBook(bookId));
    }

    // Préstamos activos que ya pasaron su fecha de entrega
    @GetMapping("/overdue")
    public ResponseEntity<List<Loan>> getOverdueLoans() {
        return ResponseEntity.ok(loanService.getOverdueLoans());
    }

    @GetMapping("/stats")
    public ResponseEntity<LoanStatsResponse> getLoanStats() {
        return ResponseEntity.ok(loanService.getLoanStats());
    }
}
