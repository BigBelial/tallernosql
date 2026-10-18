package com.noSQL.claseJava.service;

import com.mongodb.client.result.UpdateResult;
import com.noSQL.claseJava.dto.LoanRequest;
import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Loan;
import com.noSQL.claseJava.entity.LoanStatus;
import com.noSQL.claseJava.entity.User;
import com.noSQL.claseJava.exception.BusinessException;
import com.noSQL.claseJava.repository.BookRepository;
import com.noSQL.claseJava.repository.LoanRepository;
import com.noSQL.claseJava.repository.UserRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de las reglas de préstamo. Se usan mocks, así que no hace falta tener MongoDB ni Redis.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private LoanService loanService;

    private Book book;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id("user-1")
                .fullName("Ada Lovelace")
                .email("ada@mail.com")
                .build();
        book = Book.builder()
                .id("book-1")
                .title("Cien años de soledad")
                .uniqueIdentifier("ISBN-1")
                .author(Book.Author.builder().fullName("Gabriel García Márquez").build())
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(bookRepository.findById("book-1")).thenReturn(Optional.of(book));
    }

    @Test
    void createLoanRejectsBookAlreadyOnLoan() {
        book.setLoaned(true);

        assertThatThrownBy(() -> loanService.createLoan(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already on loan");
        verify(loanRepository, never()).insert(any(Loan.class));
    }

    @Test
    void createLoanRejectsUserWithMaxActiveLoans() {
        when(loanRepository.countByUserIdAndStatus("user-1", LoanStatus.ACTIVE)).thenReturn(3L);

        assertThatThrownBy(() -> loanService.createLoan(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active loans");
        verify(loanRepository, never()).insert(any(Loan.class));
    }

    @Test
    void createLoanRejectsUserWithOverdueLoans() {
        when(loanRepository.existsByUserIdAndStatusAndDueDateBefore(eq("user-1"), eq(LoanStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> loanService.createLoan(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("overdue");
        verify(loanRepository, never()).insert(any(Loan.class));
    }

    // Simula que otra persona prestó el libro justo antes (la actualización atómica no encontró el libro libre)
    @Test
    void createLoanFailsWhenAnotherRequestTookTheCopyFirst() {
        bookClaimMatches(0);

        assertThatThrownBy(() -> loanService.createLoan(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already on loan");
        verify(loanRepository, never()).insert(any(Loan.class));
    }

    // Si el índice único rechaza el préstamo, el libro se debe liberar otra vez
    @Test
    void createLoanReleasesTheBookWhenTheDatabaseRejectsTheLoan() {
        bookClaimMatches(1);
        when(loanRepository.insert(any(Loan.class))).thenThrow(new DuplicateKeyException("duplicate key"));

        assertThatThrownBy(() -> loanService.createLoan(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already on loan");
        // Una actualización para marcar el libro como prestado y otra para deshacerlo
        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Book.class));
    }

    @Test
    void createLoanCopiesBookAndUserDataIntoTheLoan() {
        bookClaimMatches(1);
        when(loanRepository.insert(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Loan loan = loanService.createLoan(request());

        assertThat(loan.getId()).isNotBlank();
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(loan.getBookTitle()).isEqualTo("Cien años de soledad");
        assertThat(loan.getBookAuthor()).isEqualTo("Gabriel García Márquez");
        assertThat(loan.getUserFullName()).isEqualTo("Ada Lovelace");
        assertThat(loan.getUserEmail()).isEqualTo("ada@mail.com");
        assertThat(loan.getDueDate()).isEqualTo(loan.getLoanDate().plusDays(14));
    }

    @Test
    void returnLoanRejectsLoanAlreadyReturned() {
        Loan returned = Loan.builder()
                .id("loan-1")
                .status(LoanStatus.RETURNED)
                .returnDate(LocalDate.now())
                .build();
        when(loanRepository.findById("loan-1")).thenReturn(Optional.of(returned));

        assertThatThrownBy(() -> loanService.returnLoan("loan-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already returned");
    }

    @Test
    void returnLoanMarksLateReturnWhenDueDateAlreadyPassed() {
        Loan active = Loan.builder()
                .id("loan-1")
                .bookId("book-1")
                .userId("user-1")
                .status(LoanStatus.ACTIVE)
                .loanDate(LocalDate.now().minusDays(20))
                .dueDate(LocalDate.now().minusDays(6))
                .build();
        when(loanRepository.findById("loan-1")).thenReturn(Optional.of(active));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Loan.class)))
                .thenReturn(Loan.builder().id("loan-1").bookId("book-1").userId("user-1")
                        .status(LoanStatus.RETURNED).build());

        loanService.returnLoan("loan-1");

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(),
                any(FindAndModifyOptions.class), eq(Loan.class));
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set.get("status")).isEqualTo(LoanStatus.RETURNED);
        assertThat(set.get("returnedLate")).isEqualTo(true);
    }

    private LoanRequest request() {
        return LoanRequest.builder().userId("user-1").bookId("book-1").build();
    }

    // Resultado de la actualización que marca el libro como prestado (0 = el libro ya no estaba libre)
    private void bookClaimMatches(long matchedCount) {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Book.class)))
                .thenReturn(UpdateResult.acknowledged(matchedCount, matchedCount, null));
    }
}
