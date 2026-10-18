package com.noSQL.claseJava.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class LoanRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotBlank(message = "bookId is required")
    private String bookId;

    // Es opcional: si no se envía, el préstamo es de 14 días
    @Min(value = 1, message = "loanDays must be at least 1")
    @Max(value = 30, message = "loanDays must be at most 30")
    private Integer loanDays;
}
