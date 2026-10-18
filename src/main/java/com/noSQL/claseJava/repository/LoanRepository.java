package com.noSQL.claseJava.repository;

import com.noSQL.claseJava.entity.Loan;
import com.noSQL.claseJava.entity.LoanStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDate;
import java.util.List;

public interface LoanRepository extends MongoRepository<Loan, String> {

    List<Loan> findByUserIdOrderByLoanDateDesc(String userId);

    List<Loan> findByUserIdAndStatusOrderByLoanDateDesc(String userId, LoanStatus status);

    List<Loan> findByStatusOrderByLoanDateDesc(LoanStatus status);

    List<Loan> findByBookIdOrderByLoanDateDesc(String bookId);

    // Préstamos vencidos = ACTIVOS y con fecha de entrega antes de hoy.
    // Se usa una consulta derivada para que la fecha se convierta igual que cuando se guardó.
    List<Loan> findByStatusAndDueDateBeforeOrderByDueDateAsc(LoanStatus status, LocalDate date);

    // Préstamos que un usuario devolvió tarde
    @Query(value = "{ 'userId': ?0, 'returnedLate': true }", sort = "{ 'returnDate': -1 }")
    List<Loan> findLateReturnsByUser(String userId);

    long countByStatus(LoanStatus status);

    long countByUserIdAndStatus(String userId, LoanStatus status);

    long countByStatusAndDueDateBefore(LoanStatus status, LocalDate date);

    long countByReturnedLate(Boolean returnedLate);

    boolean existsByUserIdAndStatusAndDueDateBefore(String userId, LoanStatus status, LocalDate date);

    boolean existsByUserIdAndBookId(String userId, String bookId);
}
