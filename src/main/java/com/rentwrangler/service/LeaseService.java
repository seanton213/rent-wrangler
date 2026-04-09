package com.rentwrangler.service;

import com.rentwrangler.context.RequestContext;
import com.rentwrangler.domain.entity.Lease;
import com.rentwrangler.domain.entity.Tenant;
import com.rentwrangler.domain.entity.Unit;
import com.rentwrangler.domain.enums.LeaseStatus;
import com.rentwrangler.domain.enums.UnitStatus;
import com.rentwrangler.dto.request.LeaseRequest;
import com.rentwrangler.dto.response.LeaseResponse;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.exception.LeaseConflictException;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.LeasePersistenceService;
import com.rentwrangler.repository.TenantRepository;
import com.rentwrangler.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaseService {

    private final LeasePersistenceService persistenceService;
    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final RequestContext requestContext;

    public PagedResponse<LeaseResponse> findAll(Pageable pageable) {
        return PagedResponse.from(persistenceService.findAll(pageable).map(LeaseResponse::from));
    }

    public LeaseResponse findById(Long id) {
        return persistenceService.findById(id)
                .map(LeaseResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", id));
    }

    public PagedResponse<LeaseResponse> findByTenant(Long tenantId, Pageable pageable) {
        return PagedResponse.from(persistenceService.findByTenantId(tenantId, pageable)
                .map(LeaseResponse::from));
    }

    public PagedResponse<LeaseResponse> findByProperty(Long propertyId, LeaseStatus status, Pageable pageable) {
        return PagedResponse.from(persistenceService.findByPropertyId(propertyId, status, pageable)
                .map(LeaseResponse::from));
    }

    public List<LeaseResponse> findExpiringWithin(int days) {
        LocalDate from = LocalDate.now();
        LocalDate to   = LocalDate.now().plusDays(days);
        return persistenceService.findExpiringBetween(from, to)
                .stream()
                .map(LeaseResponse::from)
                .toList();
    }

    @Transactional
    public LeaseResponse create(LeaseRequest request) {
        if (persistenceService.hasActiveLeaseForUnit(request.getUnitId())) {
            throw new LeaseConflictException(request.getUnitId());
        }

        Unit unit = unitRepository.findById(request.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", request.getUnitId()));
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", request.getTenantId()));

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Lease end date must be after start date.");
        }

        log.info("[{}] Creating lease for unit {} / tenant {} by '{}'",
                requestContext.getRequestId(), unit.getId(), tenant.getId(), requestContext.getUsername());

        Lease lease = Lease.builder()
                .unit(unit)
                .tenant(tenant)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .monthlyRent(request.getMonthlyRent())
                .securityDeposit(request.getSecurityDeposit())
                .status(LeaseStatus.ACTIVE)
                .build();

        unit.setStatus(UnitStatus.OCCUPIED);
        unitRepository.save(unit);

        return LeaseResponse.from(persistenceService.save(lease));
    }

    @Transactional
    public LeaseResponse terminate(Long id, String reason) {
        Lease lease = persistenceService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", id));

        if (lease.getStatus() != LeaseStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE leases can be terminated.");
        }

        lease.setStatus(LeaseStatus.TERMINATED);
        lease.setTerminationDate(LocalDate.now());
        lease.setTerminationReason(reason);

        Unit unit = lease.getUnit();
        unit.setStatus(UnitStatus.VACANT);
        unitRepository.save(unit);

        log.info("[{}] Lease {} terminated by '{}'",
                requestContext.getRequestId(), id, requestContext.getUsername());

        return LeaseResponse.from(persistenceService.save(lease));
    }
}
