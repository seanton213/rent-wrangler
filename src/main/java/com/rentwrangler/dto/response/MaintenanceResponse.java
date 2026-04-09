package com.rentwrangler.dto.response;

import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.domain.enums.MaintenanceStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class MaintenanceResponse {
    Long id;
    Long unitId;
    String unitNumber;
    Long propertyId;
    String propertyName;
    Long tenantId;
    MaintenanceCategory category;
    MaintenancePriority priority;
    MaintenanceStatus status;
    String title;
    String description;
    String vendorName;
    String vendorContact;
    BigDecimal estimatedCost;
    BigDecimal actualCost;
    Instant slaDeadline;
    Instant assignedAt;
    Instant completedAt;
    Instant createdAt;
    Instant updatedAt;

    public static MaintenanceResponse from(MaintenanceRequest r) {
        return MaintenanceResponse.builder()
                .id(r.getId())
                .unitId(r.getUnit().getId())
                .unitNumber(r.getUnit().getUnitNumber())
                .propertyId(r.getUnit().getProperty().getId())
                .propertyName(r.getUnit().getProperty().getName())
                .tenantId(r.getTenant() != null ? r.getTenant().getId() : null)
                .category(r.getCategory())
                .priority(r.getPriority())
                .status(r.getStatus())
                .title(r.getTitle())
                .description(r.getDescription())
                .vendorName(r.getVendorName())
                .vendorContact(r.getVendorContact())
                .estimatedCost(r.getEstimatedCost())
                .actualCost(r.getActualCost())
                .slaDeadline(r.getSlaDeadline())
                .assignedAt(r.getAssignedAt())
                .completedAt(r.getCompletedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
