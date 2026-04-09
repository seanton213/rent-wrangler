package com.rentwrangler.service;

import com.rentwrangler.context.RequestContext;
import com.rentwrangler.domain.entity.Lease;
import com.rentwrangler.domain.entity.Property;
import com.rentwrangler.domain.entity.Tenant;
import com.rentwrangler.domain.entity.Unit;
import com.rentwrangler.domain.enums.LeaseStatus;
import com.rentwrangler.domain.enums.UnitStatus;
import com.rentwrangler.dto.request.LeaseRequest;
import com.rentwrangler.dto.response.LeaseResponse;
import com.rentwrangler.exception.LeaseConflictException;
import com.rentwrangler.exception.ResourceNotFoundException;
import com.rentwrangler.persistence.LeasePersistenceService;
import com.rentwrangler.repository.TenantRepository;
import com.rentwrangler.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LeaseServiceTest {

    @Mock private LeasePersistenceService persistenceService;
    @Mock private UnitRepository unitRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private RequestContext requestContext;

    @InjectMocks
    private LeaseService leaseService;

    private Unit vacantUnit;
    private Tenant tenant;
    private Lease activeLease;

    @BeforeEach
    void setUp() {
        Property property = Property.builder().id(1L).name("Riverside").build();

        vacantUnit = Unit.builder()
                .id(10L)
                .property(property)
                .unitNumber("101")
                .status(UnitStatus.VACANT)
                .monthlyRent(new BigDecimal("1500.00"))
                .version(0L)
                .build();

        tenant = Tenant.builder()
                .id(20L)
                .firstName("Jordan")
                .lastName("Alvarez")
                .email("jordan@test.com")
                .governmentId("123-45-6789")
                .version(0L)
                .build();

        activeLease = Lease.builder()
                .id(100L)
                .unit(vacantUnit)
                .tenant(tenant)
                .startDate(LocalDate.now().minusMonths(3))
                .endDate(LocalDate.now().plusMonths(9))
                .monthlyRent(new BigDecimal("1500.00"))
                .securityDeposit(new BigDecimal("1500.00"))
                .status(LeaseStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    // -----------------------------------------------------------------------
    // create
    // -----------------------------------------------------------------------

    @Test
    void create_withVacantUnit_createsLeaseAndSetsUnitOccupied() {
        LeaseRequest request = buildLeaseRequest(10L, 20L);
        given(persistenceService.hasActiveLeaseForUnit(10L)).willReturn(false);
        given(unitRepository.findById(10L)).willReturn(Optional.of(vacantUnit));
        given(tenantRepository.findById(20L)).willReturn(Optional.of(tenant));
        given(persistenceService.save(any(Lease.class))).willAnswer(inv -> {
            Lease l = inv.getArgument(0);
            l.setId(100L);
            l.setCreatedAt(Instant.now());
            l.setUpdatedAt(Instant.now());
            return l;
        });

        LeaseResponse response = leaseService.create(request);

        assertThat(response.getStatus()).isEqualTo(LeaseStatus.ACTIVE);
        assertThat(response.getUnitId()).isEqualTo(10L);
        assertThat(response.getTenantId()).isEqualTo(20L);

        // Verify unit was set to OCCUPIED
        ArgumentCaptor<Unit> unitCaptor = ArgumentCaptor.forClass(Unit.class);
        verify(unitRepository).save(unitCaptor.capture());
        assertThat(unitCaptor.getValue().getStatus()).isEqualTo(UnitStatus.OCCUPIED);
    }

    @Test
    void create_whenUnitAlreadyHasActiveLease_throwsLeaseConflictException() {
        given(persistenceService.hasActiveLeaseForUnit(10L)).willReturn(true);

        assertThatThrownBy(() -> leaseService.create(buildLeaseRequest(10L, 20L)))
                .isInstanceOf(LeaseConflictException.class)
                .hasMessageContaining("10");

        verify(persistenceService, never()).save(any());
    }

    @Test
    void create_whenUnitNotFound_throwsResourceNotFoundException() {
        given(persistenceService.hasActiveLeaseForUnit(10L)).willReturn(false);
        given(unitRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> leaseService.create(buildLeaseRequest(10L, 20L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_whenTenantNotFound_throwsResourceNotFoundException() {
        given(persistenceService.hasActiveLeaseForUnit(10L)).willReturn(false);
        given(unitRepository.findById(10L)).willReturn(Optional.of(vacantUnit));
        given(tenantRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> leaseService.create(buildLeaseRequest(10L, 99L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_whenEndDateBeforeStartDate_throwsIllegalArgument() {
        given(persistenceService.hasActiveLeaseForUnit(10L)).willReturn(false);
        given(unitRepository.findById(10L)).willReturn(Optional.of(vacantUnit));
        given(tenantRepository.findById(20L)).willReturn(Optional.of(tenant));

        LeaseRequest request = buildLeaseRequest(10L, 20L);
        request.setStartDate(LocalDate.now().plusDays(30));
        request.setEndDate(LocalDate.now());  // end before start

        assertThatThrownBy(() -> leaseService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end date");
    }

    // -----------------------------------------------------------------------
    // terminate
    // -----------------------------------------------------------------------

    @Test
    void terminate_activeLeaseWithReason_setsTerminatedAndVacatesUnit() {
        given(persistenceService.findById(100L)).willReturn(Optional.of(activeLease));
        given(persistenceService.save(any(Lease.class))).willAnswer(inv -> inv.getArgument(0));

        LeaseResponse response = leaseService.terminate(100L, "Tenant relocated");

        assertThat(response.getStatus()).isEqualTo(LeaseStatus.TERMINATED);
        assertThat(response.getTerminationDate()).isEqualTo(LocalDate.now());

        // Verify unit was set back to VACANT
        ArgumentCaptor<Unit> unitCaptor = ArgumentCaptor.forClass(Unit.class);
        verify(unitRepository).save(unitCaptor.capture());
        assertThat(unitCaptor.getValue().getStatus()).isEqualTo(UnitStatus.VACANT);
    }

    @Test
    void terminate_nonActiveLease_throwsIllegalState() {
        activeLease.setStatus(LeaseStatus.EXPIRED);
        given(persistenceService.findById(100L)).willReturn(Optional.of(activeLease));

        assertThatThrownBy(() -> leaseService.terminate(100L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void terminate_leaseNotFound_throwsResourceNotFoundException() {
        given(persistenceService.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> leaseService.terminate(999L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private LeaseRequest buildLeaseRequest(Long unitId, Long tenantId) {
        LeaseRequest r = new LeaseRequest();
        r.setUnitId(unitId);
        r.setTenantId(tenantId);
        r.setStartDate(LocalDate.now());
        r.setEndDate(LocalDate.now().plusYears(1));
        r.setMonthlyRent(new BigDecimal("1500.00"));
        r.setSecurityDeposit(new BigDecimal("1500.00"));
        return r;
    }
}
