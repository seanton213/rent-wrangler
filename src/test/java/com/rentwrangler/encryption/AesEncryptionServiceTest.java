package com.rentwrangler.encryption;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesEncryptionServiceTest {

    // 32 bytes (256 bits), base64-encoded
    private static final String TEST_KEY =
            Base64.getEncoder().encodeToString("this-is-a-32-byte-test-key-12345".getBytes());

    private final AesEncryptionService service = new AesEncryptionService(TEST_KEY);

    @Test
    void encryptAndDecryptRoundTrip() {
        String plaintext = "123-45-6789";
        String ciphertext = service.encrypt(plaintext);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(service.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void sameInputProducesDifferentCiphertexts() {
        // Each call uses a fresh random IV
        String a = service.encrypt("secret");
        String b = service.encrypt("secret");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void decryptingTamperedDataThrows() {
        String ciphertext = service.encrypt("sensitive");
        String tampered = ciphertext.substring(0, ciphertext.length() - 4) + "XXXX";

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void wrongKeySizeThrows() {
        String shortKey = Base64.getEncoder().encodeToString("tooshort".getBytes());

        assertThatThrownBy(() -> new AesEncryptionService(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32-byte key");
    }
}
