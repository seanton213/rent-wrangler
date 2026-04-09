package com.rentwrangler.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption service.
 *
 * <p>AES-256-GCM is an authenticated encryption algorithm that is:
 * <ul>
 *   <li>Free — ships with every JDK via the {@code javax.crypto} API</li>
 *   <li>NIST-approved and used widely in TLS 1.3</li>
 *   <li>Authenticated — the GCM tag detects tampering without a separate MAC</li>
 * </ul>
 *
 * <p>Wire format: {@code Base64( IV[12 bytes] || ciphertext || GCM-tag[16 bytes] )}
 */
@Service
public class AesEncryptionService implements EncryptionService {

    private static final String ALGORITHM      = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH      = 12;   // 96-bit IV is optimal for GCM
    private static final int    TAG_LENGTH     = 128;  // bits

    private final SecretKeySpec secretKey;
    private final SecureRandom  secureRandom = new SecureRandom();

    public AesEncryptionService(@Value("${app.encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                "AES-256 requires a 32-byte key; provided key is " + keyBytes.length + " bytes");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV so decrypt() can extract it
            byte[] payload = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, payload, IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);

            byte[] iv            = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encryptedData = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));

            return new String(cipher.doFinal(encryptedData), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed — data may be corrupted or key is wrong", e);
        }
    }
}
