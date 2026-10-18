package com.noSQL.claseJava.repository;

import com.noSQL.claseJava.entity.Editorial;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface EditorialRepository extends MongoRepository<Editorial, String> {
    Optional<Editorial> findByName(String name);
}
