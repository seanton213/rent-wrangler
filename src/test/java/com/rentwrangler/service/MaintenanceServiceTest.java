package com.rentwrangler.service;

import com.rentwrangler.context.RequestContext;
import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.entity.Property;
import com.rentwrangler.domain.entity.Tenant;
import com.rentwrangler.domain.entity.Unit;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.domain.enums.MaintenanceStatus;
import com.rentwrangler.dto.request.CreateMaintenanceRequest;
import com.rentwrangler.dto.response.MaintenanceResponse;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.MaintenancePersistenceService;
import com.rentwrangler.repository.TenantRepository;
import com.rentwrangler.repository.UnitRepository;
import com.rentwrangler.strategy.MaintenanceStrategy;
import com.rentwrangler.strategy.MaintenanceStrategyFactory;
import com.rentwrangler.strategy.dto.MaintenanceAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock private MaintenancePersistenceService persistenceService;
    @Mock private MaintenanceStrategyFactory strategyFactory;
    @Mock private UnitRepository unitRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private RequestContext requestContext;
    @Mock private MaintenanceStrategy plumbingStrategy;

    @InjectMocks
    private MaintenanceService maintenanceService;

    private Unit unit;
    private MaintenanceRequest savedRequest;

    @BeforeEach
    void setUp() {
        Property property = Property.builder().id(1L).name("Riverside").build();
        unit = Unit.builder().id(10L).property(property).unitNumber("101").version(0L).build();

        savedRequest = MaintenanceRequest.builder()
                .id(50L)
                .unit(unit)
                .category(MaintenanceCategory.PLUMBING)
                .priority(MaintenancePriority.HIGH)
                .status(MaintenanceStatus.OPEN)
                .title("Leak under sink")
                .description("Water pooling under kitchen sink")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    // -----------------------------------------------------------------------
    // submit
    // -----------------------------------------------------------------------

    @Test
    void submit_callsStrategyFactoryAndAppliesAssignment() {
        CreateMaintenanceRequest dto = buildCreateRequest(MaintenanceCategory.PLUMBING);
        given(unitRepository.findById(10L)).willReturn(Optional.of(unit));
        given(persistenceService.save(any())).willReturn(savedRequest);
        given(strategyFactory.getStrategy(MaintenanceCategory.PLUMBING)).willReturn(plumbingStrategy);
        given(plumbingStrategy.assign(any())).willReturn(buildAssignment());

        MaintenanceResponse response = maintenanceService.submit(dto);

        // Factory must be called with the right category
        verify(strategyFactory).getStrategy(MaintenanceCategory.PLUMBING);
        // Strategy must be called with the saved request
        verify(plumbingStrategy).assign(any());

        assertThat(response.getStatus()).isEqualTo(MaintenanceStatus.ASSIGNED);
    }

    @Test
    void submit_strategyAssignmentAppliedToEntity() {
        CreateMaintenanceRequest dto = buildCreateRequest(MaintenanceCategory.PLUMBING);
        given(unitRepository.findById(10L)).willReturn(Optional.of(unit));
        given(persistenceService.save(any())).willAnswer(inv -> {
            MaintenanceRequest r = inv.getArgument(0);
            // Simulate ID assignment on first save, assignment data on second
            if (r.getId() == null) {
                r.setId(50L);
            }
            return r;
        });
        given(strategyFactory.getStrategy(MaintenanceCategory.PLUMBING)).willReturn(plumbingStrategy);
        given(plumbingStrategy.assign(any())).willReturn(buildAssignment());

        maintenanceService.submit(dto);

        // Capture the entity passed on the second save (after strategy assignment)
        ArgumentCaptor<MaintenanceRequest> captor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        verify(persistenceService, org.mockito.Mockito.times(2)).save(captor.capture());

        MaintenanceRequest assignedRequest = captor.getAllValues().get(1);
        assertThat(assignedRequest.getVendorName()).isEqualTo("Pacific Plumbing Co.");
        assertThat(assignedRequest.getEstimatedCost()).isEqualByComparingTo("275.00");
        assertThat(assignedRequest.getStatus()).isEqualTo(MaintenanceStatus.ASSIGNED);
        assertThat(assignedRequest.getAssignedAt()).isNotNull();
    }

    @Test
    void submit_withOptionalTenant_tenantLoadedWhenProvided() {
        Tenant tenant = Tenant.builder().id(20L).firstName("Jordan").lastName("Alvarez")
                .email("j@test.com").governmentId("123").version(0L).build();

        CreateMaintenanceRequest dto = buildCreateRequest(MaintenanceCategory.PLUMBING);
        dto.setTenantId(20L);

        given(unitRepository.findById(10L)).willReturn(Optional.of(unit));
        given(tenantRepository.findById(20L)).willReturn(Optional.of(tenant));
        given(persistenceService.save(any())).willReturn(savedRequest);
        given(strategyFactory.getStrategy(any())).willReturn(plumbingStrategy);
        given(plumbingStrategy.assign(any())).willReturn(buildAssignment());

        maintenanceService.submit(dto);

        verify(tenantRepository).findById(20L);
    }

    @Test
    void submit_whenUnitNotFound_throwsResourceNotFoundException() {
        given(unitRepository.findById(99L)).willReturn(Optional.empty());

        CreateMaintenanceRequest dto = buildCreateRequest(MaintenanceCategory.PLUMBING);
        dto.setUnitId(99L);

        assertThatThrownBy(() -> maintenanceService.submit(dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // updateStatus
    // -----------------------------------------------------------------------

    @Test
    void updateStatus_toCompleted_setsCompletedAtAndActualCost() {
        given(persistenceService.findById(50L)).willReturn(Optional.of(savedRequest));
        given(persistenceService.save(any())).willAnswer(inv -> inv.getArgument(0));

        MaintenanceResponse response = maintenanceService.updateStatus(50L, MaintenanceStatus.COMPLETED, 210.0);

        assertThat(response.getStatus()).isEqualTo(MaintenanceStatus.COMPLETED);
        assertThat(response.getCompletedAt()).isNotNull();
        assertThat(response.getActualCost()).isEqualByComparingTo("210.00");
    }

    @Test
    void updateStatus_toInProgress_doesNotSetCompletedAt() {
        given(persistenceService.findById(50L)).willReturn(Optional.of(savedRequest));
        given(persistenceService.save(any())).willAnswer(inv -> inv.getArgument(0));

        MaintenanceResponse response = maintenanceService.updateStatus(50L, MaintenanceStatus.IN_PROGRESS, null);

        assertThat(response.getStatus()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
        assertThat(response.getCompletedAt()).isNull();
    }

    @Test
    void updateStatus_whenNotFound_throwsResourceNotFoundException() {
        given(persistenceService.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> maintenanceService.updateStatus(999L, MaintenanceStatus.COMPLETED, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private CreateMaintenanceRequest buildCreateRequest(MaintenanceCategory category) {
        CreateMaintenanceRequest r = new CreateMaintenanceRequest();
        r.setUnitId(10L);
        r.setCategory(category);
        r.setPriority(MaintenancePriority.HIGH);
        r.setTitle("Leak under sink");
        r.setDescription("Water pooling under kitchen sink");
        return r;
    }

    private MaintenanceAssignment buildAssignment() {
        return MaintenanceAssignment.builder()
                .vendorName("Pacific Plumbing Co.")
                .vendorContact("503-555-7100")
                .resolvedPriority(MaintenancePriority.HIGH)
                .estimatedCost(new BigDecimal("275.00"))
                .slaHours(24)
                .requiresUnitAccess(true)
                .requiresLicensedContractor(true)
                .specialInstructions("Shut off water first.")
                .slaDeadline(Instant.now().plusSeconds(86400))
                .build();
    }
}
