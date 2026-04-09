package com.rentwrangler.strategy;

import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.strategy.dto.MaintenanceAssignment;

import java.math.BigDecimal;

/**
 * Strategy interface for handling a specific {@link MaintenanceCategory}.
 *
 * <p>Each implementation encapsulates:
 * <ul>
 *   <li>Vendor assignment rules for the category</li>
 *   <li>Cost estimation logic (flat rate, hourly, or by scope)</li>
 *   <li>SLA hours based on priority</li>
 *   <li>Licensing and access requirements</li>
 * </ul>
 *
 * <p>Implementations are Spring beans collected by
 * {@link MaintenanceStrategyFactory} at startup.
 */
public interface MaintenanceStrategy {

    /** The category this strategy handles. Used by the factory for routing. */
    MaintenanceCategory getCategory();

    /**
     * Produces a fully-populated {@link MaintenanceAssignment} from the request.
     * This is the primary entry point called by the service layer.
     */
    MaintenanceAssignment assign(MaintenanceRequest request);

    /** Estimate cost before vendor assignment (used for budget approval workflows). */
    BigDecimal estimateCost(MaintenanceRequest request);

    /** Hours from ticket creation to expected resolution for the given priority. */
    int getSlaHours(MaintenanceRequest request);

    boolean requiresLicensedContractor();
}
