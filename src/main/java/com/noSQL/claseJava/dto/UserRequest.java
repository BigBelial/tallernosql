package com.noSQL.claseJava.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class UserRequest {

    @NotBlank(message = "fullName is required")
    @Size(max = 120, message = "fullName must have at most 120 characters")
    private String fullName;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    private String email;

    @Size(max = 30, message = "phone must have at most 30 characters")
    private String phone;

    @Size(max = 200, message = "address must have at most 200 characters")
    private String address;
}
