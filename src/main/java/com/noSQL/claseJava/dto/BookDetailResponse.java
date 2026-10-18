package com.noSQL.claseJava.dto;

import com.noSQL.claseJava.entity.Book;
import com.noSQL.claseJava.entity.Review;
import lombok.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class BookDetailResponse implements Serializable {

    private Book book;

    @Builder.Default
    private List<Review> reviews = new ArrayList<>();
}
