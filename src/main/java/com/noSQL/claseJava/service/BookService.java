package com.noSQL.claseJava.service;

import com.noSQL.claseJava.dto.BookDetailResponse;
import com.noSQL.claseJava.dto.BookRequest;
import com.noSQL.claseJava.entity.Book;

import java.util.List;
import java.util.Optional;

// Operaciones de los libros. La implementación está en service/impl/BookServiceImpl
public interface BookService {

    // Crea el libro. El autor, la editorial y las categorías se buscan por nombre (o se crean)
    Book createBook(BookRequest request);

    List<Book> getAllBooks();

    Optional<Book> getBookById(String id);

    // El libro junto con sus reseñas
    Optional<BookDetailResponse> getBookDetail(String id);

    List<Book> getBooksByAuthor(String authorFullName);

    List<Book> getBooksByEditorial(String editorialName);

    List<Book> getBooksByCategory(String categoryName);

    List<Book> getAvailableBooks();

    List<Book> getLoanedBooks();

    // Los más prestados
    List<Book> getPopularBooks(int limit);

    // Los de mejor promedio que tengan al menos minReviews reseñas
    List<Book> getTopRatedBooks(int limit, int minReviews);

    // Edita el libro y copia el cambio a préstamos, reseñas, favoritos, etc. Devuelve null si no existe
    Book updateBook(String id, BookRequest request);

    // Devuelve false si el libro no existe. No se puede borrar un libro prestado
    boolean deleteBook(String id);
}
