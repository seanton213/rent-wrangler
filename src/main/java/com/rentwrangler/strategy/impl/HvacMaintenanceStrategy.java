package com.rentwrangler.strategy.impl;

import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.strategy.MaintenanceStrategy;
import com.rentwrangler.strategy.dto.MaintenanceAssignment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * HVAC requires an EPA Section 608 certified technician.
 * During peak summer/winter months, SLA is tightened by one tier.
 */
@Component
public class HvacMaintenanceStrategy implements MaintenanceStrategy {

    private static final String VENDOR_NAME    = "Cascade Climate Systems";
    private static final String VENDOR_CONTACT = "503-555-6200";

    @Override
    public MaintenanceCategory getCategory() {
        return MaintenanceCategory.HVAC;
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
                .requiresLicensedContractor(true)
                .specialInstructions("Technician must be EPA Section 608 certified. " +
                        "Provide service report including refrigerant levels and filter status.")
                .slaDeadline(Instant.now().plus(slaHours, ChronoUnit.HOURS))
                .build();
    }

    @Override
    public BigDecimal estimateCost(MaintenanceRequest request) {
        BigDecimal base = switch (request.getPriority()) {
            case EMERGENCY -> new BigDecimal("600.00");
            case HIGH      -> new BigDecimal("350.00");
            case NORMAL    -> new BigDecimal("220.00");
            case LOW       -> new BigDecimal("120.00");
        };
        // Peak season surcharge (July–Aug and Dec–Feb)
        return isPeakSeason() ? base.multiply(new BigDecimal("1.25")) : base;
    }

    @Override
    public int getSlaHours(MaintenanceRequest request) {
        int base = switch (request.getPriority()) {
            case EMERGENCY -> 4;
            case HIGH      -> 24;
            case NORMAL    -> 48;
            case LOW       -> 96;
        };
        // Tighten SLA during peak season
        return isPeakSeason() && request.getPriority() != MaintenancePriority.EMERGENCY
                ? base / 2
                : base;
    }

    @Override
    public boolean requiresLicensedContractor() {
        return true;
    }

    private boolean isPeakSeason() {
        Month month = Instant.now().atZone(ZoneId.systemDefault()).getMonth();
        return month == Month.JULY || month == Month.AUGUST
                || month == Month.DECEMBER || month == Month.JANUARY || month == Month.FEBRUARY;
    }
}
