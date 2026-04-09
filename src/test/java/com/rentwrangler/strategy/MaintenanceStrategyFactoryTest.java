package com.rentwrangler.strategy;

import com.rentwrangler.domain.entity.MaintenanceRequest;
import com.rentwrangler.domain.entity.Unit;
import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import com.rentwrangler.strategy.dto.MaintenanceAssignment;
import com.rentwrangler.strategy.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceStrategyFactoryTest {

    private MaintenanceStrategyFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MaintenanceStrategyFactory(List.of(
                new PlumbingMaintenanceStrategy(),
                new ElectricalMaintenanceStrategy(),
                new HvacMaintenanceStrategy(),
                new GeneralMaintenanceStrategy(),
                new PestControlMaintenanceStrategy()
        ));
    }

    @Test
    void shouldReturnCorrectStrategyForEachCategory() {
        assertThat(factory.getStrategy(MaintenanceCategory.PLUMBING))
                .isInstanceOf(PlumbingMaintenanceStrategy.class);
        assertThat(factory.getStrategy(MaintenanceCategory.ELECTRICAL))
                .isInstanceOf(ElectricalMaintenanceStrategy.class);
        assertThat(factory.getStrategy(MaintenanceCategory.HVAC))
                .isInstanceOf(HvacMaintenanceStrategy.class);
        assertThat(factory.getStrategy(MaintenanceCategory.GENERAL))
                .isInstanceOf(GeneralMaintenanceStrategy.class);
        assertThat(factory.getStrategy(MaintenanceCategory.PEST_CONTROL))
                .isInstanceOf(PestControlMaintenanceStrategy.class);
    }

    @Test
    void electricalEmergencyShouldHaveTightestSla() {
        MaintenanceRequest req = buildRequest(MaintenanceCategory.ELECTRICAL, MaintenancePriority.EMERGENCY,
                "Exposed wiring", "Exposed wires in closet");
        MaintenanceAssignment assignment = factory.getStrategy(MaintenanceCategory.ELECTRICAL).assign(req);

        assertThat(assignment.getSlaHours()).isEqualTo(2);
        assertThat(assignment.isRequiresLicensedContractor()).isTrue();
        assertThat(assignment.getSpecialInstructions()).contains("SAFETY HAZARD");
    }

    @Test
    void pestControlWithBedBugsShouldEscalatePriority() {
        MaintenanceRequest req = buildRequest(MaintenanceCategory.PEST_CONTROL, MaintenancePriority.NORMAL,
                "Possible bed bugs", "Tenant reported bed bug bites and sightings on mattress");
        MaintenanceAssignment assignment = factory.getStrategy(MaintenanceCategory.PEST_CONTROL).assign(req);

        assertThat(assignment.getResolvedPriority()).isEqualTo(MaintenancePriority.HIGH);
        assertThat(assignment.getSpecialInstructions()).contains("adjacent units");
    }

    @Test
    void plumbingLeakShouldEscalateFromLowToHigh() {
        MaintenanceRequest req = buildRequest(MaintenanceCategory.PLUMBING, MaintenancePriority.LOW,
                "Small kitchen leak", "Minor drip");
        MaintenanceAssignment assignment = factory.getStrategy(MaintenanceCategory.PLUMBING).assign(req);

        assertThat(assignment.getResolvedPriority()).isEqualTo(MaintenancePriority.HIGH);
    }

    @Test
    void generalMaintenanceShouldNotRequireLicense() {
        MaintenanceRequest req = buildRequest(MaintenanceCategory.GENERAL, MaintenancePriority.LOW,
                "Broken screen", "Window screen torn");
        MaintenanceStrategy strategy = factory.getStrategy(MaintenanceCategory.GENERAL);

        assertThat(strategy.requiresLicensedContractor()).isFalse();
    }

    private MaintenanceRequest buildRequest(MaintenanceCategory category,
                                             MaintenancePriority priority,
                                             String title,
                                             String description) {
        return MaintenanceRequest.builder()
                .unit(Unit.builder().id(1L).build())
                .category(category)
                .priority(priority)
                .title(title)
                .description(description)
                .build();
    }
}
