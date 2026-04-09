package com.rentwrangler.encryption;

public interface EncryptionService {

    /**
     * Encrypts {@code plaintext} using AES-256-GCM.
     *
     * @return Base64-encoded string of the form {@code IV || ciphertext || GCM-tag}
     */
    String encrypt(String plaintext);

    /**
     * Decrypts a value previously produced by {@link #encrypt(String)}.
     *
     * @return the original plaintext
     */
    String decrypt(String ciphertext);
}
