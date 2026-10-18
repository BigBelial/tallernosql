package com.noSQL.claseJava.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Document(collection = "favorites")
public class Favorites implements Serializable {

    @Id
    private String id;
    // Hay un solo documento de favoritos por usuario (índice único en userId)
    private String userId;
    private String userFullName;

    // Los datos de cada libro se copian dentro de la lista: así toda la lista se lee con una sola consulta
    @Builder.Default
    private List<FavoriteBook> books = new ArrayList<>();
    private LocalDateTime updatedAt;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FavoriteBook {
        private String bookId;
        private String title;
        private String authorName;
        private LocalDateTime addedAt;
    }
}
