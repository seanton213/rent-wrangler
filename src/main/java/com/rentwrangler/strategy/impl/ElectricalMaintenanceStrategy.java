package com.rentwrangler.strategy.impl;

import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.strategy.MaintenanceStrategy;
import com.rentwrangler.strategy.dto.MaintenanceAssignment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Electrical work requires a licensed electrician and a safety inspection sign-off.
 * Any EMERGENCY electrical issue takes the unit offline until resolved.
 */
@Component
public class ElectricalMaintenanceStrategy implements MaintenanceStrategy {

    private static final String VENDOR_NAME    = "Volt Electric Services";
    private static final String VENDOR_CONTACT = "503-555-8500";

    @Override
    public MaintenanceCategory getCategory() {
        return MaintenanceCategory.ELECTRICAL;
    }

    @Override
    public MaintenanceAssignment assign(MaintenanceRequest request) {
        int slaHours = getSlaHours(request);

        String instructions = "Electrician must provide safety inspection report post-completion.";
        if (request.getPriority() == MaintenancePriority.EMERGENCY) {
            instructions = "SAFETY HAZARD — mark unit OFFLINE immediately. " +
                    "Conduct full panel inspection before restoring power.";
        }

        return MaintenanceAssignment.builder()
                .vendorName(VENDOR_NAME)
                .vendorContact(VENDOR_CONTACT)
                .resolvedPriority(request.getPriority())
                .estimatedCost(estimateCost(request))
                .slaHours(slaHours)
                .requiresUnitAccess(true)
                .requiresLicensedContractor(true)
                .specialInstructions(instructions)
                .slaDeadline(Instant.now().plus(slaHours, ChronoUnit.HOURS))
                .build();
    }

    @Override
    public BigDecimal estimateCost(MaintenanceRequest request) {
        return switch (request.getPriority()) {
            case EMERGENCY -> new BigDecimal("900.00");
            case HIGH      -> new BigDecimal("500.00");
            case NORMAL    -> new BigDecimal("300.00");
            case LOW       -> new BigDecimal("150.00");
        };
    }

    @Override
    public int getSlaHours(MaintenanceRequest request) {
        return switch (request.getPriority()) {
            case EMERGENCY -> 2;
            case HIGH      -> 8;
            case NORMAL    -> 48;
            case LOW       -> 96;
        };
    }

    @Override
    public boolean requiresLicensedContractor() {
        return true;
    }
}
