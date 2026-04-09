package com.rentwrangler.strategy.dto;

import com.rentwrangler.domain.enums.MaintenancePriority;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable result of running a {@link com.rentwrangler.strategy.MaintenanceStrategy}.
 * Contains all the information needed to update a {@link com.rentwrangler.domain.entity.MaintenanceRequest}.
 */
@Value
@Builder
public class MaintenanceAssignment {
    String vendorName;
    String vendorContact;
    MaintenancePriority resolvedPriority;
    BigDecimal estimatedCost;
    int slaHours;
    boolean requiresUnitAccess;
    boolean requiresLicensedContractor;
    String specialInstructions;
    Instant slaDeadline;
}
