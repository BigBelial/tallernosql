package com.noSQL.claseJava.dto;

import lombok.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class LoanStatsResponse implements Serializable {

    private Long totalBooks;
    private Long booksOnLoan;
    private Long availableBooks;

    private Long totalLoans;
    private Long activeLoans;
    private Long returnedLoans;
    private Long overdueLoans;
    private Long returnedLateLoans;

    @Builder.Default
    private List<BookLoanCount> mostLoanedBooks = new ArrayList<>();
    @Builder.Default
    private List<UserLoanCount> topBorrowers = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class BookLoanCount {
        private String bookId;
        private String bookTitle;
        private String bookAuthor;
        private Long loanCount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UserLoanCount {
        private String userId;
        private String userFullName;
        private Long loanCount;
    }
}
