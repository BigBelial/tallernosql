package com.noSQL.claseJava.repository;

import com.noSQL.claseJava.entity.Author;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface AuthorRepository extends MongoRepository<Author, String> {
    Optional<Author> findByFullName(String fullName);
}
