package com.rentwrangler.dto.response;

import com.rentwrangler.domain.entity.Lease;
import com.rentwrangler.domain.enums.LeaseStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Value
@Builder
public class LeaseResponse {
    Long id;
    Long unitId;
    String unitNumber;
    Long propertyId;
    String propertyName;
    Long tenantId;
    String tenantFullName;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal monthlyRent;
    BigDecimal securityDeposit;
    LeaseStatus status;
    LocalDate terminationDate;
    Instant createdAt;
    Instant updatedAt;

    public static LeaseResponse from(Lease l) {
        return LeaseResponse.builder()
                .id(l.getId())
                .unitId(l.getUnit().getId())
                .unitNumber(l.getUnit().getUnitNumber())
                .propertyId(l.getUnit().getProperty().getId())
                .propertyName(l.getUnit().getProperty().getName())
                .tenantId(l.getTenant().getId())
                .tenantFullName(l.getTenant().getFirstName() + " " + l.getTenant().getLastName())
                .startDate(l.getStartDate())
                .endDate(l.getEndDate())
                .monthlyRent(l.getMonthlyRent())
                .securityDeposit(l.getSecurityDeposit())
                .status(l.getStatus())
                .terminationDate(l.getTerminationDate())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }
}
