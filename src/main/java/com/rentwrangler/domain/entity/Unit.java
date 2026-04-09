package com.rentwrangler.domain.entity;

import com.rentwrangler.domain.enums.UnitStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "units", indexes = {
        @Index(name = "idx_units_property_id", columnList = "property_id"),
        @Index(name = "idx_units_status",       columnList = "status"),
        @Index(name = "idx_units_rent",         columnList = "monthly_rent")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "unit_number", nullable = false, length = 20)
    private String unitNumber;

    @Column
    private Integer floor;

    @Column(nullable = false)
    @Builder.Default
    private Integer bedrooms = 1;

    @Column(nullable = false, precision = 3, scale = 1)
    @Builder.Default
    private BigDecimal bathrooms = BigDecimal.ONE;

    @Column(name = "square_footage")
    private Integer squareFootage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UnitStatus status = UnitStatus.VACANT;

    @Column(name = "monthly_rent", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyRent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
