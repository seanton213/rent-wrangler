package com.rentwrangler.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TenantRequest {

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @NotBlank
    @Email
    private String email;

    @Pattern(regexp = "\\d{3}-\\d{3}-\\d{4}", message = "Phone must be in format 555-555-5555")
    private String phone;

    @Past
    private LocalDate dateOfBirth;

    /**
     * Government ID (SSN or driver's license). Accepted as plaintext in the request;
     * encrypted at rest by the Hibernate event listener.
     */
    @NotBlank
    @Size(min = 4, max = 20)
    private String governmentId;

    @Size(max = 200)
    private String emergencyContactName;

    @Pattern(regexp = "\\d{3}-\\d{3}-\\d{4}", message = "Phone must be in format 555-555-5555")
    private String emergencyContactPhone;
}
