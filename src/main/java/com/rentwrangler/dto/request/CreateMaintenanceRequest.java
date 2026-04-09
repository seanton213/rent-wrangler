package com.rentwrangler.dto.request;

import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.domain.enums.MaintenancePriority;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateMaintenanceRequest {

    @NotNull
    private Long unitId;

    private Long tenantId;

    @NotNull
    private MaintenanceCategory category;

    private MaintenancePriority priority = MaintenancePriority.NORMAL;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    private String description;
}
