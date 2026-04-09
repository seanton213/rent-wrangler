package com.rentwrangler.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class LeaseRequest {

    @NotNull
    private Long unitId;

    @NotNull
    private Long tenantId;

    @NotNull
    @FutureOrPresent
    private LocalDate startDate;

    @NotNull
    @Future
    private LocalDate endDate;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal monthlyRent;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal securityDeposit;
}
