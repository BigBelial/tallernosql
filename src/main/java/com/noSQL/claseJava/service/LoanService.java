package com.noSQL.claseJava.service;

import com.mongodb.client.result.UpdateResult;
import com.noSQL.claseJava.config.CacheNames;
import com.noSQL.claseJava.dto.LoanRequest;
import com.noSQL.claseJava.dto.LoanStatsResponse;
import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Loan;
import com.noSQL.claseJava.entity.LoanStatus;
import com.noSQL.claseJava.entity.User;
import com.noSQL.claseJava.exception.BusinessException;
import com.noSQL.claseJava.exception.ResourceNotFoundException;
import com.noSQL.claseJava.repository.BookRepository;
import com.noSQL.claseJava.repository.LoanRepository;
import com.noSQL.claseJava.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@RequiredArgsConstructor
@Service
public class LoanService {

    static final int DEFAULT_LOAN_DAYS = 14;
    static final int MAX_ACTIVE_LOANS = 3;
    // Cuántos préstamos se guardan en el resumen del usuario
    static final int RECENT_LOANS_LIMIT = 5;
    private static final int TOP_LIMIT = 5;

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    // Un préstamo cambia el libro, el usuario, las listas de préstamos y las estadísticas,
    // por eso se limpian todas esas cachés
    @CacheEvict(cacheNames = {CacheNames.LOANS, CacheNames.LOAN_STATS, CacheNames.USERS,
            CacheNames.BOOKS, CacheNames.BOOK_DETAILS, CacheNames.BOOK_QUERIES}, allEntries = true)
    public Loan createLoan(LoanRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));
        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book", request.getBookId()));

        // Reglas de negocio antes de prestar
        if (Boolean.TRUE.equals(book.getLoaned())) {
            throw alreadyOnLoan(book.getTitle());
        }
        LocalDate today = LocalDate.now();
        if (loanRepository.countByUserIdAndStatus(user.getId(), LoanStatus.ACTIVE) >= MAX_ACTIVE_LOANS) {
            throw new BusinessException("User already has " + MAX_ACTIVE_LOANS + " active loans");
        }
        if (loanRepository.existsByUserIdAndStatusAndDueDateBefore(user.getId(), LoanStatus.ACTIVE, today)) {
            throw new BusinessException("User has overdue loans and cannot borrow more books");
        }

        int loanDays = request.getLoanDays() != null ? request.getLoanDays() : DEFAULT_LOAN_DAYS;
        if (loanDays < 1 || loanDays > 30) {
            throw new IllegalArgumentException("loanDays must be between 1 and 30");
        }

        // El id se genera antes para poder guardarlo también dentro del libro (currentLoan)
        Loan loan = Loan.builder()
                .id(new ObjectId().toHexString())
                .bookId(book.getId())
                .bookTitle(book.getTitle())
                .bookAuthor(DenormalizationService.authorNameOf(book))
                .bookUniqueIdentifier(book.getUniqueIdentifier())
                .userId(user.getId())
                .userFullName(user.getFullName())
                .userEmail(user.getEmail())
                .loanDate(today)
                .dueDate(today.plusDays(loanDays))
                .status(LoanStatus.ACTIVE)
                .returnedLate(false)
                .build();

        // Paso 1: marcar el libro como prestado
        claimBook(loan);

        // Paso 2: guardar el préstamo. Si falla porque el índice único dice que ya hay un préstamo
        // activo de ese libro, se deshace el paso 1
        try {
            loan = loanRepository.insert(loan);
        } catch (DuplicateKeyException e) {
            releaseBook(loan.getBookId(), loan.getId(), true);
            throw alreadyOnLoan(book.getTitle());
        }

        // Paso 3: actualizar el resumen de actividad del usuario
        addLoanToUserActivity(loan);
        return loan;
    }

    // Marca el libro como prestado SOLO si no está prestado, todo en una sola operación de MongoDB.
    // Así, si dos personas piden el mismo libro al mismo tiempo, solo una lo consigue.
    private void claimBook(Loan loan) {
        Book.CurrentLoan currentLoan = Book.CurrentLoan.builder()
                .loanId(loan.getId())
                .userId(loan.getUserId())
                .userFullName(loan.getUserFullName())
                .loanDate(loan.getLoanDate())
                .dueDate(loan.getDueDate())
                .build();

        UpdateResult result = mongoTemplate.updateFirst(
                query(where("id").is(loan.getBookId()).and("loaned").ne(true)),
                new Update()
                        .set("loaned", true)
                        .set("available", false)
                        .set("currentLoan", currentLoan)
                        .inc("totalLoans", 1),
                Book.class);

        if (result.getMatchedCount() == 0) {
            throw alreadyOnLoan(loan.getBookTitle());
        }
    }

    // Deja el libro disponible otra vez (solo si el préstamo actual del libro es este)
    private void releaseBook(String bookId, String loanId, boolean undoLoanCounter) {
        Update update = new Update()
                .set("loaned", false)
                .set("available", true)
                .unset("currentLoan");
        if (undoLoanCounter) {
            update.inc("totalLoans", -1);
        }
        mongoTemplate.updateFirst(query(where("id").is(bookId).and("currentLoan.loanId").is(loanId)),
                update, Book.class);
    }

    private void addLoanToUserActivity(Loan loan) {
        Update update = new Update()
                .inc("activity.activeLoans", 1)
                .inc("activity.totalLoans", 1)
                .set("activity.lastLoanDate", loan.getLoanDate());
        // Se agrega al inicio de la lista y se dejan solo los últimos RECENT_LOANS_LIMIT
        update.push("activity.recentLoans")
                .atPosition(Update.Position.FIRST)
                .slice(RECENT_LOANS_LIMIT)
                .each(toSummary(loan));
        mongoTemplate.updateFirst(query(where("id").is(loan.getUserId())), update, User.class);
    }

    private User.LoanSummary toSummary(Loan loan) {
        return User.LoanSummary.builder()
                .loanId(loan.getId())
                .bookId(loan.getBookId())
                .bookTitle(loan.getBookTitle())
                .loanDate(loan.getLoanDate())
                .dueDate(loan.getDueDate())
                .status(loan.getStatus())
                .build();
    }

    @CacheEvict(cacheNames = {CacheNames.LOANS, CacheNames.LOAN_STATS, CacheNames.USERS,
            CacheNames.BOOKS, CacheNames.BOOK_DETAILS, CacheNames.BOOK_QUERIES}, allEntries = true)
    public Loan returnLoan(String loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", loanId));
        if (loan.getStatus() == LoanStatus.RETURNED) {
            throw new BusinessException("Loan " + loanId + " was already returned on " + loan.getReturnDate());
        }

        LocalDate today = LocalDate.now();
        boolean returnedLate = loan.getDueDate() != null && today.isAfter(loan.getDueDate());

        // Se marca como devuelto SOLO si sigue activo, así no se puede devolver dos veces
        Loan returned = mongoTemplate.findAndModify(
                query(where("id").is(loanId).and("status").is(LoanStatus.ACTIVE)),
                new Update()
                        .set("status", LoanStatus.RETURNED)
                        .set("returnDate", today)
                        .set("returnedLate", returnedLate),
                FindAndModifyOptions.options().returnNew(true),
                Loan.class);
        if (returned == null) {
            throw new BusinessException("Loan " + loanId + " was already returned");
        }

        // El libro vuelve a estar disponible
        releaseBook(returned.getBookId(), returned.getId(), false);

        // Se actualiza el resumen del usuario: un préstamo activo menos y el estado en sus últimos préstamos
        mongoTemplate.updateFirst(query(where("id").is(returned.getUserId())),
                new Update().inc("activity.activeLoans", -1), User.class);
        mongoTemplate.updateFirst(
                query(where("id").is(returned.getUserId()).and("activity.recentLoans.loanId").is(loanId)),
                new Update()
                        .set("activity.recentLoans.$.status", LoanStatus.RETURNED)
                        .set("activity.recentLoans.$.returnDate", today),
                User.class);
        return returned;
    }

    @Cacheable(CacheNames.LOANS)
    public List<Loan> getAllLoans() {
        return loanRepository.findAll();
    }

    @Cacheable(cacheNames = CacheNames.LOANS, unless = "#result == null")
    public Optional<Loan> getLoanById(String id) {
        return loanRepository.findById(id);
    }

    @Cacheable(CacheNames.LOANS)
    public List<Loan> getLoansByUser(String userId) {
        return loanRepository.findByUserIdOrderByLoanDateDesc(userId);
    }

    @Cacheable(CacheNames.LOANS)
    public List<Loan> getLoansByUserAndStatus(String userId, LoanStatus status) {
        return loanRepository.findByUserIdAndStatusOrderByLoanDateDesc(userId, status);
    }

    @Cacheable(CacheNames.LOANS)
    public List<Loan> getLateReturnsByUser(String userId) {
        return loanRepository.findLateReturnsByUser(userId);
    }

    @Cacheable(CacheNames.LOANS)
    public List<Loan> getLoansByStatus(LoanStatus status) {
        return loanRepository.findByStatusOrderByLoanDateDesc(status);
    }

    @Cacheable(CacheNames.LOANS)
    public List<Loan> getLoansByBook(String bookId) {
        return loanRepository.findByBookIdOrderByLoanDateDesc(bookId);
    }

    @Cacheable(CacheNames.LOANS)
    public List<Loan> getOverdueLoans() {
        return loanRepository.findByStatusAndDueDateBeforeOrderByDueDateAsc(LoanStatus.ACTIVE, LocalDate.now());
    }

    @Cacheable(CacheNames.LOAN_STATS)
    public LoanStatsResponse getLoanStats() {
        LocalDate today = LocalDate.now();
        long totalBooks = bookRepository.count();
        long booksOnLoan = bookRepository.countByLoaned(true);

        return LoanStatsResponse.builder()
                .totalBooks(totalBooks)
                .booksOnLoan(booksOnLoan)
                .availableBooks(totalBooks - booksOnLoan)
                .totalLoans(loanRepository.count())
                .activeLoans(loanRepository.countByStatus(LoanStatus.ACTIVE))
                .returnedLoans(loanRepository.countByStatus(LoanStatus.RETURNED))
                .overdueLoans(loanRepository.countByStatusAndDueDateBefore(LoanStatus.ACTIVE, today))
                .returnedLateLoans(loanRepository.countByReturnedLate(true))
                .mostLoanedBooks(getMostLoanedBooks())
                .topBorrowers(getTopBorrowers())
                .build();
    }

    // Como cada préstamo ya trae el título del libro copiado, el ranking sale sin hacer $lookup a "books"
    private List<LoanStatsResponse.BookLoanCount> getMostLoanedBooks() {
        Aggregation aggregation = newAggregation(
                group("bookId")
                        .count().as("loanCount")
                        .first("bookTitle").as("bookTitle")
                        .first("bookAuthor").as("bookAuthor"),
                sort(Sort.Direction.DESC, "loanCount"),
                limit(TOP_LIMIT));

        List<LoanStatsResponse.BookLoanCount> result = new ArrayList<>();
        for (Document doc : mongoTemplate.aggregate(aggregation, Loan.class, Document.class).getMappedResults()) {
            result.add(LoanStatsResponse.BookLoanCount.builder()
                    .bookId(String.valueOf(doc.get("_id")))
                    .bookTitle(doc.getString("bookTitle"))
                    .bookAuthor(doc.getString("bookAuthor"))
                    .loanCount(doc.get("loanCount", Number.class).longValue())
                    .build());
        }
        return result;
    }

    // Igual que arriba: el nombre del usuario ya viene copiado en cada préstamo, no hace falta $lookup a "users"
    private List<LoanStatsResponse.UserLoanCount> getTopBorrowers() {
        Aggregation aggregation = newAggregation(
                group("userId")
                        .count().as("loanCount")
                        .first("userFullName").as("userFullName"),
                sort(Sort.Direction.DESC, "loanCount"),
                limit(TOP_LIMIT));

        List<LoanStatsResponse.UserLoanCount> result = new ArrayList<>();
        for (Document doc : mongoTemplate.aggregate(aggregation, Loan.class, Document.class).getMappedResults()) {
            result.add(LoanStatsResponse.UserLoanCount.builder()
                    .userId(String.valueOf(doc.get("_id")))
                    .userFullName(doc.getString("userFullName"))
                    .loanCount(doc.get("loanCount", Number.class).longValue())
                    .build());
        }
        return result;
    }

    private BusinessException alreadyOnLoan(String bookTitle) {
        return new BusinessException("Book '" + bookTitle + "' is already on loan (there is only one physical copy)");
    }
}
