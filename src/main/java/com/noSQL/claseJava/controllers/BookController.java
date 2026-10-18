package com.noSQL.claseJava.controllers;

import com.noSQL.claseJava.dto.BookDetailResponse;
import com.noSQL.claseJava.dto.BookRequest;
import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @PostMapping
    public ResponseEntity<Book> createBook(@RequestBody BookRequest request) {
        Book createdBook = bookService.createBook(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdBook);
    }

    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        List<Book> books = bookService.getAllBooks();
        return ResponseEntity.ok(books);
    }

    // Al consultar un libro también se devuelven sus reseñas
    @GetMapping("/{id}")
    public ResponseEntity<BookDetailResponse> getBookById(@PathVariable String id) {
        Optional<BookDetailResponse> book = bookService.getBookDetail(id);
        return book.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search/author/{author}")
    public ResponseEntity<List<Book>> getBooksByAuthor(@PathVariable String author) {
        List<Book> books = bookService.getBooksByAuthor(author);
        return ResponseEntity.ok(books);
    }

    @GetMapping("/search/editorial/{editorial}")
    public ResponseEntity<List<Book>> getBooksByEditorial(@PathVariable String editorial) {
        List<Book> books = bookService.getBooksByEditorial(editorial);
        return ResponseEntity.ok(books);
    }

    @GetMapping("/search/category/{category}")
    public ResponseEntity<List<Book>> getBooksByCategory(@PathVariable String category) {
        List<Book> books = bookService.getBooksByCategory(category);
        return ResponseEntity.ok(books);
    }

    @GetMapping("/search/available")
    public ResponseEntity<List<Book>> getAvailableBooks() {
        List<Book> books = bookService.getAvailableBooks();
        return ResponseEntity.ok(books);
    }

    @GetMapping("/search/loaned")
    public ResponseEntity<List<Book>> getLoanedBooks() {
        List<Book> books = bookService.getLoanedBooks();
        return ResponseEntity.ok(books);
    }

    // Libros más prestados
    @GetMapping("/popular")
    public ResponseEntity<List<Book>> getPopularBooks(@RequestParam(defaultValue = "5") int limit) {
        List<Book> books = bookService.getPopularBooks(limit);
        return ResponseEntity.ok(books);
    }

    // Libros mejor calificados (minReviews = mínimo de reseñas que debe tener el libro)
    @GetMapping("/top-rated")
    public ResponseEntity<List<Book>> getTopRatedBooks(@RequestParam(defaultValue = "5") int limit,
                                                       @RequestParam(defaultValue = "1") int minReviews) {
        List<Book> books = bookService.getTopRatedBooks(limit, minReviews);
        return ResponseEntity.ok(books);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable String id, @RequestBody BookRequest request) {
        Book updatedBook = bookService.updateBook(id, request);
        if (updatedBook != null) {
            return ResponseEntity.ok(updatedBook);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable String id) {
        if (bookService.deleteBook(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
