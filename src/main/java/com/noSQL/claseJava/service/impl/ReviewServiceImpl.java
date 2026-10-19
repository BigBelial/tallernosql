package com.noSQL.claseJava.service.impl;

import com.noSQL.claseJava.service.ReviewService;
import com.noSQL.claseJava.service.DenormalizationService;

import com.noSQL.claseJava.config.CacheNames;
import com.noSQL.claseJava.dto.ReviewRequest;
import com.noSQL.claseJava.dto.ReviewUpdateRequest;
import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Review;
import com.noSQL.claseJava.entity.User;
import com.noSQL.claseJava.exception.BusinessException;
import com.noSQL.claseJava.exception.ResourceNotFoundException;
import com.noSQL.claseJava.repository.BookRepository;
import com.noSQL.claseJava.repository.LoanRepository;
import com.noSQL.claseJava.repository.ReviewRepository;
import com.noSQL.claseJava.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@RequiredArgsConstructor
@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final LoanRepository loanRepository;
    private final DenormalizationService denormalizationService;
    private final MongoTemplate mongoTemplate;

    // Una reseña cambia el promedio del libro y el contador del usuario, por eso se limpian esas cachés
    @CacheEvict(cacheNames = {CacheNames.REVIEWS, CacheNames.BOOKS, CacheNames.BOOK_DETAILS,
            CacheNames.BOOK_QUERIES, CacheNames.USERS}, allEntries = true)
    @Override
    public Review createReview(ReviewRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));
        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book", request.getBookId()));

        // Reglas de negocio
        validateRating(request.getRating());
        if (reviewRepository.existsByBookIdAndUserId(book.getId(), user.getId())) {
            throw new BusinessException("User already reviewed this book, update the existing review instead");
        }
        if (!loanRepository.existsByUserIdAndBookId(user.getId(), book.getId())) {
            throw new BusinessException("Only users who have borrowed the book can review it");
        }

        // Se copian el título del libro y el nombre del usuario dentro de la reseña (denormalización)
        LocalDateTime now = LocalDateTime.now();
        Review review = Review.builder()
                .bookId(book.getId())
                .bookTitle(book.getTitle())
                .userId(user.getId())
                .userFullName(user.getFullName())
                .rating(request.getRating())
                .comment(request.getComment())
                .createdAt(now)
                .updatedAt(now)
                .build();
        // El índice único (bookId, userId) también evita reseñas repetidas si llegan dos peticiones a la vez
        review = reviewRepository.insert(review);

        // Se recalcula el promedio del libro y se suma 1 al contador de reseñas del usuario
        denormalizationService.refreshBookRating(book.getId());
        mongoTemplate.updateFirst(query(where("id").is(user.getId())),
                new Update().inc("activity.totalReviews", 1), User.class);
        return review;
    }

    @Cacheable(CacheNames.REVIEWS)
    @Override
    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }

    @Cacheable(cacheNames = CacheNames.REVIEWS, unless = "#result == null")
    @Override
    public Optional<Review> getReviewById(String id) {
        return reviewRepository.findById(id);
    }

    // Si se manda minRating, solo salen las reseñas con esa calificación o más
    @Cacheable(CacheNames.REVIEWS)
    @Override
    public List<Review> getReviewsByBook(String bookId, Integer minRating) {
        if (minRating != null) {
            return reviewRepository.findByBookIdWithMinRating(bookId, minRating);
        }
        return reviewRepository.findByBookIdOrderByCreatedAtDesc(bookId);
    }

    @Cacheable(CacheNames.REVIEWS)
    @Override
    public List<Review> getReviewsByUser(String userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @CacheEvict(cacheNames = {CacheNames.REVIEWS, CacheNames.BOOKS, CacheNames.BOOK_DETAILS,
            CacheNames.BOOK_QUERIES}, allEntries = true)
    @Override
    public Review updateReview(String id, ReviewUpdateRequest request) {
        Optional<Review> existingReview = reviewRepository.findById(id);
        if (existingReview.isEmpty()) {
            return null;
        }
        validateRating(request.getRating());

        Review review = existingReview.get();
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setUpdatedAt(LocalDateTime.now());
        review = reviewRepository.save(review);

        // Si cambió la calificación, cambia el promedio del libro
        denormalizationService.refreshBookRating(review.getBookId());
        return review;
    }

    @CacheEvict(cacheNames = {CacheNames.REVIEWS, CacheNames.BOOKS, CacheNames.BOOK_DETAILS,
            CacheNames.BOOK_QUERIES, CacheNames.USERS}, allEntries = true)
    @Override
    public boolean deleteReview(String id) {
        Optional<Review> review = reviewRepository.findById(id);
        if (review.isEmpty()) {
            return false;
        }
        reviewRepository.deleteById(id);

        // Se recalcula el promedio del libro y se resta 1 al contador de reseñas del usuario
        denormalizationService.refreshBookRating(review.get().getBookId());
        mongoTemplate.updateFirst(query(where("id").is(review.get().getUserId())),
                new Update().inc("activity.totalReviews", -1), User.class);
        return true;
    }

    // La calificación debe ser un número entero de 1 a 5
    private void validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
    }
}
