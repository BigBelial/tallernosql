package com.noSQL.claseJava.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Document(collection = "reviews")
public class Review implements Serializable {

    @Id
    private String id;

    // Ids + copia de datos del libro y del usuario, para no tener que hacer "joins"
    // cuando se listan las reseñas de un libro o de un usuario
    private String bookId;
    private String bookTitle;
    private String userId;
    private String userFullName;

    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
