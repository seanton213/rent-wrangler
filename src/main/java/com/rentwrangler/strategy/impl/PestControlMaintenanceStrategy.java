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
 * Pest control requires a licensed exterminator and typically necessitates
 * temporary unit vacancy for chemical treatments. Adjacent units must be
 * notified when rodents or bed bugs are involved.
 */
@Component
public class PestControlMaintenanceStrategy implements MaintenanceStrategy {

    private static final String VENDOR_NAME    = "GreatNW Pest Solutions";
    private static final String VENDOR_CONTACT = "503-555-9900";

    @Override
    public MaintenanceCategory getCategory() {
        return MaintenanceCategory.PEST_CONTROL;
    }

    @Override
    public MaintenanceAssignment assign(MaintenanceRequest request) {
        int slaHours = getSlaHours(request);
        boolean highSpreadRisk = isHighSpreadRisk(request);

        String instructions = "Tenant must vacate unit for minimum 4 hours post-treatment.";
        if (highSpreadRisk) {
            instructions += " Notify adjacent units and assess for infestation spread. " +
                    "Property manager approval required before treatment.";
        }

        return MaintenanceAssignment.builder()
                .vendorName(VENDOR_NAME)
                .vendorContact(VENDOR_CONTACT)
                .resolvedPriority(highSpreadRisk ? MaintenancePriority.HIGH : request.getPriority())
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
        BigDecimal base = switch (request.getPriority()) {
            case EMERGENCY -> new BigDecimal("500.00");
            case HIGH      -> new BigDecimal("300.00");
            case NORMAL    -> new BigDecimal("200.00");
            case LOW       -> new BigDecimal("150.00");
        };
        // Spread risk (bed bugs, rodents) requires more extensive treatment
        return isHighSpreadRisk(request) ? base.multiply(new BigDecimal("1.50")) : base;
    }

    @Override
    public int getSlaHours(MaintenanceRequest request) {
        return switch (request.getPriority()) {
            case EMERGENCY -> 8;
            case HIGH      -> 24;
            case NORMAL    -> 48;
            case LOW       -> 72;
        };
    }

    @Override
    public boolean requiresLicensedContractor() {
        return true;
    }

    private boolean isHighSpreadRisk(MaintenanceRequest request) {
        String lower = request.getDescription().toLowerCase();
        return lower.contains("bed bug") || lower.contains("bedbug")
                || lower.contains("rodent") || lower.contains("rat")
                || lower.contains("mouse") || lower.contains("mice");
    }
}
