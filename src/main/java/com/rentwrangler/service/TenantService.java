package com.rentwrangler.service;

import com.rentwrangler.context.RequestContext;
import com.rentwrangler.domain.entity.Tenant;
import com.rentwrangler.dto.request.TenantRequest;
import com.rentwrangler.dto.response.PagedResponse;
import com.rentwrangler.dto.response.TenantResponse;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.TenantPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantPersistenceService persistenceService;
    private final RequestContext requestContext;

    public PagedResponse<TenantResponse> findAll(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return PagedResponse.from(persistenceService.search(search, pageable)
                    .map(TenantResponse::from));
        }
        return PagedResponse.from(persistenceService.findAll(pageable).map(TenantResponse::from));
    }

    public TenantResponse findById(Long id) {
        return persistenceService.findById(id)
                .map(TenantResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));
    }

    public PagedResponse<TenantResponse> findActiveTenants(Pageable pageable) {
        return PagedResponse.from(persistenceService.findActiveTenants(pageable)
                .map(TenantResponse::from));
    }

    public PagedResponse<TenantResponse> findWithExpiringLeases(int daysAhead, Pageable pageable) {
        return PagedResponse.from(persistenceService.findWithExpiringLeases(daysAhead, pageable)
                .map(TenantResponse::from));
    }

    @Transactional
    public TenantResponse create(TenantRequest request) {
        if (persistenceService.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("A tenant with email '" + request.getEmail() + "' already exists.");
        }

        log.info("[{}] Creating tenant '{}' by user '{}'",
                requestContext.getRequestId(), request.getEmail(), requestContext.getUsername());

        Tenant tenant = Tenant.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .dateOfBirth(request.getDateOfBirth())
                // governmentId is stored as plaintext here;
                // EncryptionEventListener encrypts it before the INSERT
                .governmentId(request.getGovernmentId())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .build();

        return TenantResponse.from(persistenceService.save(tenant));
    }

    @Transactional
    public TenantResponse update(Long id, TenantRequest request) {
        Tenant tenant = persistenceService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));

        tenant.setFirstName(request.getFirstName());
        tenant.setLastName(request.getLastName());
        tenant.setPhone(request.getPhone());
        tenant.setDateOfBirth(request.getDateOfBirth());
        tenant.setGovernmentId(request.getGovernmentId());
        tenant.setEmergencyContactName(request.getEmergencyContactName());
        tenant.setEmergencyContactPhone(request.getEmergencyContactPhone());

        return TenantResponse.from(persistenceService.save(tenant));
    }

    @Transactional
    public void delete(Long id) {
        persistenceService.deleteById(id);
    }
}
