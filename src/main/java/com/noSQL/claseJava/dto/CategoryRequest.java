package com.noSQL.claseJava.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class CategoryRequest {

    private String name;
    private String description;
}
