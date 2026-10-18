package com.noSQL.claseJava.repository;

import com.noSQL.claseJava.entity.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface BookRepository extends MongoRepository<Book, String> {

    List<Book> findByAuthorFullName(String fullName);

    List<Book> findByEditorialName(String editorialName);

    List<Book> findByCategoriesName(String categoryName);

    List<Book> findByAvailable(Boolean available);

    List<Book> findByPublicationYear(Integer year);

    List<Book> findByTitleContainingIgnoreCase(String title);

    List<Book> findByLoaned(Boolean loaned);

    long countByLoaned(Boolean loaned);

    // Libros disponibles. Con "$ne: true" también salen los libros viejos que todavía no tienen el campo "loaned"
    @Query("{ 'loaned': { $ne: true } }")
    List<Book> findAvailableBooks();

    // Libros más prestados. Usa el contador guardado en el libro, así no hay que recorrer toda la colección "loans"
    @Query(value = "{ 'totalLoans': { $gt: 0 } }", sort = "{ 'totalLoans': -1, 'title': 1 }")
    List<Book> findPopularBooks(Pageable pageable);

    // Libros mejor calificados. Usa el promedio guardado en el libro, así no hay que recorrer la colección "reviews"
    @Query(value = "{ 'totalReviews': { $gte: ?0 } }", sort = "{ 'averageRating': -1, 'totalReviews': -1 }")
    List<Book> findTopRatedBooks(int minReviews, Pageable pageable);
}
