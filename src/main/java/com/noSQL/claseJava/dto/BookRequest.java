package com.noSQL.claseJava.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import com.noSQL.claseJava.entity.Book.Author;
import com.noSQL.claseJava.entity.Book.Editorial;
import com.noSQL.claseJava.entity.Book.Category;
import java.util.List;

// "available" ya no viene en la petición: si un libro está disponible o no lo decide la lógica de préstamos.
// Si alguien lo manda igual (como en el JSON del taller anterior), se ignora en vez de dar error.
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class BookRequest {

    private String uniqueIdentifier;
    private String title;
    private String synopsis;
    private Integer publicationYear;
    private Author author;
    private Editorial editorial;
    private List<Category> categories;
}
