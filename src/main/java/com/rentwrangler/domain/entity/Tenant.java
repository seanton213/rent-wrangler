package com.rentwrangler.domain.entity;

import com.rentwrangler.annotation.Encrypted;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tenants", indexes = {
        @Index(name = "idx_tenants_email",     columnList = "email"),
        @Index(name = "idx_tenants_last_name", columnList = "last_name")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * Government-issued ID (SSN or driver's license number).
     *
     * <p>This field is transparently encrypted by {@link com.rentwrangler.event.EncryptionEventListener}
     * before every INSERT/UPDATE and decrypted after every SELECT. The database
     * column {@code government_id_encrypted} never holds plaintext.
     */
    @Encrypted
    @Column(name = "government_id_encrypted", nullable = false, length = 512)
    private String governmentId;

    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

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
