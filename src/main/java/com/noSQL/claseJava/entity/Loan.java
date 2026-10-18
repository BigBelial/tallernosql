package com.noSQL.claseJava.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.io.Serializable;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Document(collection = "loans")
public class Loan implements Serializable {

    @Id
    private String id;

    // Id del libro + copia de sus datos: para listar préstamos no hay que ir a buscar a "books"
    private String bookId;
    private String bookTitle;
    private String bookAuthor;
    private String bookUniqueIdentifier;

    // Id del usuario + copia de sus datos
    private String userId;
    private String userFullName;
    private String userEmail;

    private LocalDate loanDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private LoanStatus status;
    private Boolean returnedLate;
}
