package com.noSQL.claseJava.repository;

import com.noSQL.claseJava.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByFullNameContainingIgnoreCase(String fullName);

    @Query("{ 'activity.activeLoans': { $gt: 0 } }")
    List<User> findUsersWithActiveLoans();

    // Usuarios que tienen ese libro en su lista de últimos préstamos (sirve para mantener esa copia actualizada)
    @Query("{ 'activity.recentLoans.bookId': ?0 }")
    List<User> findByRecentLoanBookId(String bookId);
}
