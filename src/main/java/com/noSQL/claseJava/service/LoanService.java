package com.noSQL.claseJava.service;

import com.noSQL.claseJava.dto.LoanRequest;
import com.noSQL.claseJava.dto.LoanStatsResponse;
import com.noSQL.claseJava.entity.Loan;
import com.noSQL.claseJava.entity.LoanStatus;

import java.util.List;
import java.util.Optional;

// Operaciones de los préstamos. La implementación está en service/impl/LoanServiceImpl
public interface LoanService {

    // Presta un libro (cada libro tiene una sola copia física)
    Loan createLoan(LoanRequest request);

    // Devuelve el libro y lo deja disponible otra vez
    Loan returnLoan(String loanId);

    List<Loan> getAllLoans();

    Optional<Loan> getLoanById(String id);

    List<Loan> getLoansByUser(String userId);

    List<Loan> getLoansByUserAndStatus(String userId, LoanStatus status);

    // Préstamos que el usuario devolvió tarde
    List<Loan> getLateReturnsByUser(String userId);

    List<Loan> getLoansByStatus(LoanStatus status);

    // Historial de préstamos de un libro
    List<Loan> getLoansByBook(String bookId);

    // Préstamos activos que ya pasaron su fecha de entrega
    List<Loan> getOverdueLoans();

    LoanStatsResponse getLoanStats();
}
