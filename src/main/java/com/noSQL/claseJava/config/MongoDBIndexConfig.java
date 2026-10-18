package com.noSQL.claseJava.config;

import com.noSQL.claseJava.entity.LoanStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Configuration
public class MongoDBIndexConfig {

    @Bean
    public CommandLineRunner createIndexes(MongoTemplate mongoTemplate) {
        return args -> {
            // Si MongoDB no responde, no se intenta crear ningún índice (así no se queda esperando por cada uno)
            try {
                mongoTemplate.executeCommand("{ ping: 1 }");
            } catch (Exception e) {
                log.error("MongoDB is not reachable, skipping index creation: {}", e.getMessage());
                return;
            }

            // Índices de libros
            createIndex(mongoTemplate, "books", new Index().on("author.fullName", Sort.Direction.ASC));
            createIndex(mongoTemplate, "books", new Index().on("editorial.name", Sort.Direction.ASC));
            createIndex(mongoTemplate, "books", new Index().on("categories.name", Sort.Direction.ASC));
            createIndex(mongoTemplate, "books", new Index().on("available", Sort.Direction.ASC));
            createIndex(mongoTemplate, "books", new Index().on("title", Sort.Direction.ASC));
            createIndex(mongoTemplate, "books", new Index().on("publicationYear", Sort.Direction.DESC));
            createIndex(mongoTemplate, "books", new Index().on("uniqueIdentifier", Sort.Direction.ASC).unique());
            createIndex(mongoTemplate, "books", new Index().on("loaned", Sort.Direction.ASC));
            createIndex(mongoTemplate, "books", new Index().on("totalLoans", Sort.Direction.DESC));
            createIndex(mongoTemplate, "books", new Index()
                    .on("averageRating", Sort.Direction.DESC)
                    .on("totalReviews", Sort.Direction.DESC));
            createIndex(mongoTemplate, "books", new Index().on("currentLoan.userId", Sort.Direction.ASC));

            // Índices de usuarios (el email no se puede repetir)
            createIndex(mongoTemplate, "users", new Index().on("email", Sort.Direction.ASC).unique());
            createIndex(mongoTemplate, "users", new Index().on("fullName", Sort.Direction.ASC));
            createIndex(mongoTemplate, "users", new Index().on("activity.activeLoans", Sort.Direction.ASC));
            createIndex(mongoTemplate, "users", new Index().on("activity.recentLoans.bookId", Sort.Direction.ASC));

            // Índices de préstamos
            createIndex(mongoTemplate, "loans", new Index()
                    .on("userId", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC));
            createIndex(mongoTemplate, "loans", new Index()
                    .on("status", Sort.Direction.ASC)
                    .on("dueDate", Sort.Direction.ASC));
            createIndex(mongoTemplate, "loans", new Index()
                    .on("bookId", Sort.Direction.ASC)
                    .on("loanDate", Sort.Direction.DESC));
            // Cada libro tiene una sola copia física: la base de datos no deja guardar
            // un segundo préstamo ACTIVO del mismo libro
            createIndex(mongoTemplate, "loans", new Index()
                    .on("bookId", Sort.Direction.ASC)
                    .unique()
                    .partial(PartialIndexFilter.of(Criteria.where("status").is(LoanStatus.ACTIVE.name())))
                    .named("unique_active_loan_per_book"));

            // Índices de reseñas: un usuario solo puede reseñar una vez cada libro
            createIndex(mongoTemplate, "reviews", new Index()
                    .on("bookId", Sort.Direction.ASC)
                    .on("userId", Sort.Direction.ASC)
                    .unique());
            createIndex(mongoTemplate, "reviews", new Index()
                    .on("bookId", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC));
            createIndex(mongoTemplate, "reviews", new Index().on("userId", Sort.Direction.ASC));

            // Índices de favoritos: una sola lista por usuario
            createIndex(mongoTemplate, "favorites", new Index().on("userId", Sort.Direction.ASC).unique());
            createIndex(mongoTemplate, "favorites", new Index().on("books.bookId", Sort.Direction.ASC));
        };
    }

    private void createIndex(MongoTemplate mongoTemplate, String collection, Index index) {
        try {
            mongoTemplate.indexOps(collection).createIndex(index);
        } catch (Exception e) {
            log.error("Error creating index on '{}': {}", collection, e.getMessage());
        }
    }
}
