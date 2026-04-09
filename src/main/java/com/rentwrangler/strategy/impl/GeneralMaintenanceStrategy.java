package com.rentwrangler.strategy.impl;

import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.strategy.MaintenanceStrategy;
import com.rentwrangler.strategy.dto.MaintenanceAssignment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * General maintenance (painting, fixtures, carpentry, cleaning) is handled
 * by Rent Wrangler's in-house maintenance crew with no licensing requirement.
 */
@Component
public class GeneralMaintenanceStrategy implements MaintenanceStrategy {

    private static final String VENDOR_NAME    = "Rent Wrangler Maintenance Crew";
    private static final String VENDOR_CONTACT = "maintenance@rent-wrangler.internal";

    @Override
    public MaintenanceCategory getCategory() {
        return MaintenanceCategory.GENERAL;
    }

    @Override
    public MaintenanceAssignment assign(MaintenanceRequest request) {
        int slaHours = getSlaHours(request);

        return MaintenanceAssignment.builder()
                .vendorName(VENDOR_NAME)
                .vendorContact(VENDOR_CONTACT)
                .resolvedPriority(request.getPriority())
                .estimatedCost(estimateCost(request))
                .slaHours(slaHours)
                .requiresUnitAccess(true)
                .requiresLicensedContractor(false)
                .specialInstructions("Standard tenant notification required 24 hours before entry.")
                .slaDeadline(Instant.now().plus(slaHours, ChronoUnit.HOURS))
                .build();
    }

    @Override
    public BigDecimal estimateCost(MaintenanceRequest request) {
        return switch (request.getPriority()) {
            case EMERGENCY -> new BigDecimal("200.00");
            case HIGH      -> new BigDecimal("125.00");
            case NORMAL    -> new BigDecimal("75.00");
            case LOW       -> new BigDecimal("40.00");
        };
    }

    @Override
    public int getSlaHours(MaintenanceRequest request) {
        return switch (request.getPriority()) {
            case EMERGENCY -> 8;
            case HIGH      -> 48;
            case NORMAL    -> 96;
            case LOW       -> 168;  // 7 days
        };
    }

    @Override
    public boolean requiresLicensedContractor() {
        return false;
    }
}
