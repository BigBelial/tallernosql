package com.noSQL.claseJava.repository;

import com.noSQL.claseJava.entity.Favorites;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface FavoritesRepository extends MongoRepository<Favorites, String> {

    Optional<Favorites> findByUserId(String userId);

    List<Favorites> findByBooksBookId(String bookId);

    void deleteByUserId(String userId);
}
