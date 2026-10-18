package com.noSQL.claseJava.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Document(collection = "users")
public class User implements Serializable {

    @Id
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private LocalDateTime createdAt;

    // Resumen de la actividad del usuario guardado dentro del mismo documento (denormalización).
    // Así, para ver el perfil del usuario no hay que consultar otras colecciones.
    @Builder.Default
    private ActivitySummary activity = new ActivitySummary();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ActivitySummary {
        @Builder.Default
        private Integer activeLoans = 0;
        @Builder.Default
        private Integer totalLoans = 0;
        @Builder.Default
        private Integer totalReviews = 0;
        @Builder.Default
        private Integer favoritesCount = 0;
        private LocalDate lastLoanDate;
        // Últimos préstamos, del más nuevo al más viejo. Solo se guardan unos pocos (patrón subset).
        @Builder.Default
        private List<LoanSummary> recentLoans = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class LoanSummary {
        private String loanId;
        private String bookId;
        private String bookTitle;
        private LocalDate loanDate;
        private LocalDate dueDate;
        private LocalDate returnDate;
        private LoanStatus status;
    }
}
