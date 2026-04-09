package com.rentwrangler.service;

import com.rentwrangler.context.RequestContext;
import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.entity.Tenant;
import com.rentwrangler.domain.entity.Unit;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.domain.enums.MaintenanceStatus;
import com.rentwrangler.dto.request.CreateMaintenanceRequest;
import com.rentwrangler.dto.response.MaintenanceResponse;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.MaintenancePersistenceService;
import com.rentwrangler.repository.TenantRepository;
import com.rentwrangler.repository.UnitRepository;
import com.rentwrangler.strategy.MaintenanceStrategy;
import com.rentwrangler.strategy.MaintenanceStrategyFactory;
import com.rentwrangler.strategy.dto.MaintenanceAssignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenancePersistenceService persistenceService;
    private final MaintenanceStrategyFactory strategyFactory;
    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final RequestContext requestContext;

    public PagedResponse<MaintenanceResponse> findAll(
            Long propertyId,
            MaintenanceStatus status,
            MaintenanceCategory category,
            MaintenancePriority priority,
            Pageable pageable) {

        return PagedResponse.from(
                persistenceService.findByFilters(propertyId, status, category, priority, pageable)
                        .map(MaintenanceResponse::from));
    }

    public MaintenanceResponse findById(Long id) {
        return persistenceService.findById(id)
                .map(MaintenanceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("MaintenanceRequest", id));
    }

    public PagedResponse<MaintenanceResponse> findByUnit(Long unitId, Pageable pageable) {
        return PagedResponse.from(persistenceService.findByUnitId(unitId, pageable)
                .map(MaintenanceResponse::from));
    }

    public PagedResponse<MaintenanceResponse> findByTenant(Long tenantId, Pageable pageable) {
        return PagedResponse.from(persistenceService.findByTenantId(tenantId, pageable)
                .map(MaintenanceResponse::from));
    }

    /**
     * Submits a new maintenance request and immediately runs the strategy for
     * the given category to produce a vendor assignment and SLA deadline.
     *
     * <p>The {@link MaintenanceStrategyFactory} selects the correct
     * {@link MaintenanceStrategy} implementation at runtime based on the category,
     * encapsulating all vendor, cost, and SLA logic for that work type.
     */
    @Transactional
    public MaintenanceResponse submit(CreateMaintenanceRequest dto) {
        Unit unit = unitRepository.findById(dto.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", dto.getUnitId()));

        Tenant tenant = null;
        if (dto.getTenantId() != null) {
            tenant = tenantRepository.findById(dto.getTenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant", dto.getTenantId()));
        }

        MaintenanceRequest request = MaintenanceRequest.builder()
                .unit(unit)
                .tenant(tenant)
                .category(dto.getCategory())
                .priority(dto.getPriority())
                .status(MaintenanceStatus.OPEN)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .build();

        // Save first to get an ID before running the strategy
        request = persistenceService.save(request);

        // Use the factory to select the correct strategy and produce an assignment
        MaintenanceStrategy strategy = strategyFactory.getStrategy(dto.getCategory());
        MaintenanceAssignment assignment = strategy.assign(request);

        log.info("[{}] Ticket #{} assigned via {} strategy: vendor='{}', SLA={}h, cost=${}",
                requestContext.getRequestId(), request.getId(),
                dto.getCategory(), assignment.getVendorName(),
                assignment.getSlaHours(), assignment.getEstimatedCost());

        // Apply assignment results back to the entity
        request.setVendorName(assignment.getVendorName());
        request.setVendorContact(assignment.getVendorContact());
        request.setPriority(assignment.getResolvedPriority());
        request.setEstimatedCost(assignment.getEstimatedCost());
        request.setSlaDeadline(assignment.getSlaDeadline());
        request.setStatus(MaintenanceStatus.ASSIGNED);
        request.setAssignedAt(Instant.now());

        return MaintenanceResponse.from(persistenceService.save(request));
    }

    @Transactional
    public MaintenanceResponse updateStatus(Long id, MaintenanceStatus newStatus, Double actualCost) {
        MaintenanceRequest request = persistenceService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MaintenanceRequest", id));

        request.setStatus(newStatus);
        if (newStatus == MaintenanceStatus.COMPLETED) {
            request.setCompletedAt(Instant.now());
            if (actualCost != null) {
                request.setActualCost(java.math.BigDecimal.valueOf(actualCost));
            }
        }

        log.info("[{}] Ticket #{} status updated to {} by '{}'",
                requestContext.getRequestId(), id, newStatus, requestContext.getUsername());

        return MaintenanceResponse.from(persistenceService.save(request));
    }

    @Transactional
    public void delete(Long id) {
        persistenceService.deleteById(id);
    }
}
