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
 * Plumbing work always requires a licensed plumber.
 * Emergency leaks are escalated to HIGH and dispatched same-day.
 */
@Component
public class PlumbingMaintenanceStrategy implements MaintenanceStrategy {

    private static final String VENDOR_NAME    = "Pacific Plumbing & Drain";
    private static final String VENDOR_CONTACT = "503-555-7100";

    @Override
    public MaintenanceCategory getCategory() {
        return MaintenanceCategory.PLUMBING;
    }

    @Override
    public MaintenanceAssignment assign(MaintenanceRequest request) {
        int slaHours = getSlaHours(request);
        BigDecimal cost = estimateCost(request);
        MaintenancePriority resolved = resolvePriority(request);

        return MaintenanceAssignment.builder()
                .vendorName(VENDOR_NAME)
                .vendorContact(VENDOR_CONTACT)
                .resolvedPriority(resolved)
                .estimatedCost(cost)
                .slaHours(slaHours)
                .requiresUnitAccess(true)
                .requiresLicensedContractor(true)
                .specialInstructions("Shut off water supply to unit before work begins. " +
                        "Notify tenant 1 hour prior to entry.")
                .slaDeadline(Instant.now().plus(slaHours, ChronoUnit.HOURS))
                .build();
    }

    @Override
    public BigDecimal estimateCost(MaintenanceRequest request) {
        return switch (request.getPriority()) {
            case EMERGENCY -> new BigDecimal("450.00");
            case HIGH      -> new BigDecimal("275.00");
            case NORMAL    -> new BigDecimal("175.00");
            case LOW       -> new BigDecimal("100.00");
        };
    }

    @Override
    public int getSlaHours(MaintenanceRequest request) {
        return switch (request.getPriority()) {
            case EMERGENCY -> 4;
            case HIGH      -> 24;
            case NORMAL    -> 48;
            case LOW       -> 72;
        };
    }

    @Override
    public boolean requiresLicensedContractor() {
        return true;
    }

    private MaintenancePriority resolvePriority(MaintenanceRequest request) {
        // Active water leak automatically escalates to HIGH if not already higher
        if (request.getPriority() == MaintenancePriority.LOW &&
                request.getTitle().toLowerCase().contains("leak")) {
            return MaintenancePriority.HIGH;
        }
        return request.getPriority();
    }
}
