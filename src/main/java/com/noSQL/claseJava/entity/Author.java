package com.noSQL.claseJava.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Document(collection = "authors")
public class Author {

    @Id
    private String id;
    private String fullName;
    private String nationality;
    private String biography;

    @Builder.Default
    private List<BookReference> books = new java.util.ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class BookReference {
        private String bookId;
        private String title;
    }
}
