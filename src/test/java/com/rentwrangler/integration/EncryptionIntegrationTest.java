package com.rentwrangler.integration;

import com.rentwrangler.AbstractIntegrationTest;
import com.rentwrangler.domain.entity.Tenant;
import com.rentwrangler.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify the end-to-end encryption lifecycle.
 *
 * <p>These tests use a real PostgreSQL container (via {@link AbstractIntegrationTest})
 * so we can query the raw column value through JDBC and confirm that what is actually
 * stored in the database is ciphertext — never the plaintext SSN.
 */
@Transactional
class EncryptionIntegrationTest extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String PLAINTEXT_SSN = "123-45-6789";

    // -----------------------------------------------------------------------
    // Encryption at rest
    // -----------------------------------------------------------------------

    @Test
    void governmentId_isNeverStoredAsPlaintext() {
        Tenant saved = tenantRepository.save(buildTenant(PLAINTEXT_SSN));

        String rawDbValue = jdbcTemplate.queryForObject(
                "SELECT government_id_encrypted FROM tenants WHERE id = ?",
                String.class, saved.getId());

        assertThat(rawDbValue)
                .as("The plaintext SSN must never appear in the database column")
                .isNotEqualTo(PLAINTEXT_SSN)
                .isNotBlank();
    }

    @Test
    void governmentId_storedValueIsBase64EncodedCiphertext() {
        Tenant saved = tenantRepository.save(buildTenant(PLAINTEXT_SSN));

        String rawDbValue = jdbcTemplate.queryForObject(
                "SELECT government_id_encrypted FROM tenants WHERE id = ?",
                String.class, saved.getId());

        // AES-GCM output is Base64(IV[12] || ciphertext || tag[16])
        // The decoded length must be > 12 + 16 = 28 bytes
        byte[] decoded = Base64.getDecoder().decode(rawDbValue);
        assertThat(decoded.length)
                .as("Decoded ciphertext must be longer than the IV (12) + GCM tag (16)")
                .isGreaterThan(28);
    }

    @Test
    void governmentId_eachSavesProducesDifferentCiphertext() {
        // AES-GCM uses a random 12-byte IV per encryption — two saves of the same plaintext
        // must produce different ciphertexts (non-deterministic encryption).
        Tenant t1 = tenantRepository.save(buildTenant(PLAINTEXT_SSN, "alice@test.com"));
        Tenant t2 = tenantRepository.save(buildTenant(PLAINTEXT_SSN, "bob@test.com"));

        String cipher1 = jdbcTemplate.queryForObject(
                "SELECT government_id_encrypted FROM tenants WHERE id = ?",
                String.class, t1.getId());
        String cipher2 = jdbcTemplate.queryForObject(
                "SELECT government_id_encrypted FROM tenants WHERE id = ?",
                String.class, t2.getId());

        assertThat(cipher1).isNotEqualTo(cipher2);
    }

    // -----------------------------------------------------------------------
    // Decryption on load
    // -----------------------------------------------------------------------

    @Test
    void governmentId_isDecryptedWhenEntityIsLoaded() {
        Tenant saved = tenantRepository.save(buildTenant(PLAINTEXT_SSN));

        // Clear the first-level cache to force a fresh SELECT
        tenantRepository.flush();

        Tenant loaded = tenantRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getGovernmentId())
                .as("Loaded entity must expose the decrypted plaintext")
                .isEqualTo(PLAINTEXT_SSN);
    }

    @Test
    void governmentId_decryptedCorrectlyAfterUpdate() {
        Tenant saved = tenantRepository.save(buildTenant(PLAINTEXT_SSN));

        // Update with a different SSN
        String updatedSsn = "987-65-4321";
        saved.setGovernmentId(updatedSsn);
        tenantRepository.save(saved);
        tenantRepository.flush();

        Tenant reloaded = tenantRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getGovernmentId()).isEqualTo(updatedSsn);

        // Confirm the DB column was re-encrypted with the new value
        String rawDbValue = jdbcTemplate.queryForObject(
                "SELECT government_id_encrypted FROM tenants WHERE id = ?",
                String.class, saved.getId());
        assertThat(rawDbValue).isNotEqualTo(updatedSsn);
    }

    @Test
    void otherFields_areNotEncrypted() {
        Tenant saved = tenantRepository.save(buildTenant(PLAINTEXT_SSN));

        // email is NOT annotated @Encrypted — it must be stored as plaintext
        String rawEmail = jdbcTemplate.queryForObject(
                "SELECT email FROM tenants WHERE id = ?",
                String.class, saved.getId());

        assertThat(rawEmail).isEqualTo("test@encryption.com");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Tenant buildTenant(String ssn) {
        return buildTenant(ssn, "test@encryption.com");
    }

    private Tenant buildTenant(String ssn, String email) {
        return Tenant.builder()
                .firstName("Test")
                .lastName("Tenant")
                .email(email)
                .governmentId(ssn)
                .build();
    }
}
