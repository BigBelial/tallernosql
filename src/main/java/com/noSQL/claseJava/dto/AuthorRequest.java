package com.noSQL.claseJava.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class AuthorRequest {

    private String fullName;
    private String nationality;
    private String biography;
}
