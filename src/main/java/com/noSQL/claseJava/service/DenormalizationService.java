package com.noSQL.claseJava.service;

import com.noSQL.claseJava.entity.Author;
import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Category;
import com.noSQL.claseJava.entity.Editorial;
import com.noSQL.claseJava.entity.User;

// Mantiene actualizados los datos copiados (denormalizados) en las distintas colecciones.
// La implementación está en service/impl/DenormalizationServiceImpl
public interface DenormalizationService {

    // Devuelve el nombre del autor del libro (o null si el libro no tiene autor)
    static String authorNameOf(Book book) {
        return book.getAuthor() != null ? book.getAuthor().getFullName() : null;
    }

    // Cambió el título, el autor o el ISBN de un libro
    void propagateBookChanges(Book book);

    // Cambiaron los datos de un autor (si cambió el nombre, también se actualizan préstamos y favoritos)
    void propagateAuthorChanges(Author author, boolean nameChanged);

    void propagateEditorialChanges(Editorial editorial);

    void propagateCategoryChanges(Category category);

    // Cambió el nombre o el email de un usuario
    void propagateUserChanges(User user);

    // Recalcula el promedio de calificaciones y el total de reseñas del libro
    void refreshBookRating(String bookId);

    // Se borró un libro: se quitan sus reseñas y sale de los favoritos
    void removeBookReferences(String bookId);

    // Se borró un usuario: se quitan sus favoritos y sus reseñas
    void removeUserReferences(String userId);
}
