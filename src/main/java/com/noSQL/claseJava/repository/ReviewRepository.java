package com.noSQL.claseJava.repository;

import com.noSQL.claseJava.entity.Review;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface ReviewRepository extends MongoRepository<Review, String> {

    List<Review> findByBookIdOrderByCreatedAtDesc(String bookId);

    List<Review> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query(value = "{ 'bookId': ?0, 'rating': { $gte: ?1 } }", sort = "{ 'createdAt': -1 }")
    List<Review> findByBookIdWithMinRating(String bookId, int minRating);

    boolean existsByBookIdAndUserId(String bookId, String userId);

    void deleteByBookId(String bookId);

    void deleteByUserId(String userId);
}
