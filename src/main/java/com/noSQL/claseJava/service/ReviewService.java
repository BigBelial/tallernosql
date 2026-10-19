package com.noSQL.claseJava.service;

import com.noSQL.claseJava.dto.ReviewRequest;
import com.noSQL.claseJava.dto.ReviewUpdateRequest;
import com.noSQL.claseJava.entity.Review;

import java.util.List;
import java.util.Optional;

// Operaciones de las reseñas. La implementación está en service/impl/ReviewServiceImpl
public interface ReviewService {

    // Crea la reseña y recalcula el promedio del libro
    Review createReview(ReviewRequest request);

    List<Review> getAllReviews();

    Optional<Review> getReviewById(String id);

    // Si minRating no es null, solo las reseñas con esa calificación o más
    List<Review> getReviewsByBook(String bookId, Integer minRating);

    List<Review> getReviewsByUser(String userId);

    // Edita la calificación y el comentario. Devuelve null si no existe
    Review updateReview(String id, ReviewUpdateRequest request);

    // Devuelve false si la reseña no existe
    boolean deleteReview(String id);
}
