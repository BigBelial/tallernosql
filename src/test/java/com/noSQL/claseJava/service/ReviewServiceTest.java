package com.noSQL.claseJava.service;

import com.noSQL.claseJava.dto.ReviewRequest;
import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Review;
import com.noSQL.claseJava.entity.User;
import com.noSQL.claseJava.exception.BusinessException;
import com.noSQL.claseJava.repository.BookRepository;
import com.noSQL.claseJava.repository.LoanRepository;
import com.noSQL.claseJava.repository.ReviewRepository;
import com.noSQL.claseJava.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de las reglas de reseñas. Se usan mocks, así que no hace falta tener MongoDB ni Redis.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private DenormalizationService denormalizationService;
    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        User user = User.builder().id("user-1").fullName("Ada Lovelace").email("ada@mail.com").build();
        Book book = Book.builder().id("book-1").title("Rayuela").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(bookRepository.findById("book-1")).thenReturn(Optional.of(book));
        // Por defecto: el usuario ya prestó el libro y todavía no lo ha reseñado
        when(loanRepository.existsByUserIdAndBookId("user-1", "book-1")).thenReturn(true);
        when(reviewRepository.existsByBookIdAndUserId("book-1", "user-1")).thenReturn(false);
    }

    @Test
    void createReviewRejectsSecondReviewOfTheSameBook() {
        when(reviewRepository.existsByBookIdAndUserId("book-1", "user-1")).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(request(5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already reviewed");
        verify(reviewRepository, never()).insert(any(Review.class));
    }

    @Test
    void createReviewRejectsUserWhoNeverBorrowedTheBook() {
        when(loanRepository.existsByUserIdAndBookId("user-1", "book-1")).thenReturn(false);

        assertThatThrownBy(() -> reviewService.createReview(request(4)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("borrowed");
        verify(reviewRepository, never()).insert(any(Review.class));
    }

    @Test
    void createReviewRejectsRatingOutOfRange() {
        assertThatThrownBy(() -> reviewService.createReview(request(6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
        verify(reviewRepository, never()).insert(any(Review.class));
    }

    @Test
    void createReviewCopiesDataAndRecalculatesTheBookRating() {
        when(reviewRepository.insert(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review review = reviewService.createReview(request(5));

        assertThat(review.getBookTitle()).isEqualTo("Rayuela");
        assertThat(review.getUserFullName()).isEqualTo("Ada Lovelace");
        assertThat(review.getRating()).isEqualTo(5);
        verify(denormalizationService).refreshBookRating("book-1");
    }

    private ReviewRequest request(int rating) {
        return ReviewRequest.builder()
                .userId("user-1")
                .bookId("book-1")
                .rating(rating)
                .comment("Muy bueno")
                .build();
    }
}
