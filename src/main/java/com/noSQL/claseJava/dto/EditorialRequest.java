package com.noSQL.claseJava.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class EditorialRequest {

    private String name;
    private String country;
    private String website;
}
