package com.noSQL.claseJava.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Document(collection = "books")
public class Book implements Serializable {

    @Id
    private String id;
    private String uniqueIdentifier;
    private String title;
    private String synopsis;
    private Integer publicationYear;
    // Se deja por compatibilidad con la API original: siempre es lo contrario de "loaned"
    private Boolean available;
    private Author author;
    private Editorial editorial;
    private List<Category> categories;

    // Indica si el libro está prestado. Cada libro tiene una sola copia física,
    // y este campo solo se cambia cuando se presta o se devuelve el libro.
    @Builder.Default
    private Boolean loaned = false;
    // Copia de los datos del préstamo actual (denormalización). Es null cuando el libro está disponible.
    private CurrentLoan currentLoan;
    // Cuántas veces se ha prestado el libro (sirve para sacar los libros populares rápido)
    @Builder.Default
    private Integer totalLoans = 0;

    // Promedio y total de reseñas guardados en el libro. Se recalculan cada vez que cambia una reseña.
    @Builder.Default
    private Double averageRating = 0.0;
    @Builder.Default
    private Integer totalReviews = 0;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Author {
        private String id;
        private String fullName;
        private String nationality;
        private String biography;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Editorial {
        private String id;
        private String name;
        private String country;
        private String website;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Category {
        private String id;
        private String name;
        private String description;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CurrentLoan {
        private String loanId;
        private String userId;
        private String userFullName;
        private LocalDate loanDate;
        private LocalDate dueDate;
    }
}
