package com.noSQL.claseJava.service;

import com.noSQL.claseJava.dto.AuthorRequest;
import com.noSQL.claseJava.entity.Author;

import java.util.List;
import java.util.Optional;

// Operaciones de los autores. La implementación está en service/impl/AuthorServiceImpl
public interface AuthorService {

    Author createAuthor(AuthorRequest request);

    List<Author> getAllAuthors();

    Optional<Author> getAuthorById(String id);

    Optional<Author> getAuthorByName(String fullName);

    // Libros del autor (lista guardada dentro del autor)
    List<Author.BookReference> getAuthorBooks(String authorId);

    // Edita el autor y copia el cambio a los libros que lo tienen. Devuelve null si no existe
    Author updateAuthor(String id, AuthorRequest request);

    // Devuelve false si el autor no existe
    boolean deleteAuthor(String id);
}
