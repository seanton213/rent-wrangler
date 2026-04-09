package com.rentwrangler.dto.response;

import com.rentwrangler.domain.entity.Tenant;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;

@Value
@Builder
public class TenantResponse {
    Long id;
    String firstName;
    String lastName;
    String email;
    String phone;
    LocalDate dateOfBirth;
    /** Government ID is intentionally masked in all API responses. */
    String governmentIdMasked;
    String emergencyContactName;
    String emergencyContactPhone;
    Instant createdAt;
    Instant updatedAt;

    public static TenantResponse from(Tenant t) {
        return TenantResponse.builder()
                .id(t.getId())
                .firstName(t.getFirstName())
                .lastName(t.getLastName())
                .email(t.getEmail())
                .phone(t.getPhone())
                .dateOfBirth(t.getDateOfBirth())
                .governmentIdMasked(mask(t.getGovernmentId()))
                .emergencyContactName(t.getEmergencyContactName())
                .emergencyContactPhone(t.getEmergencyContactPhone())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private static String mask(String value) {
        if (value == null || value.length() < 4) return "****";
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }
}
