package com.rentwrangler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a String field for transparent AES-256-GCM encryption at rest.
 *
 * <p>Fields annotated with {@code @Encrypted} are automatically encrypted
 * by {@link com.rentwrangler.event.EncryptionEventListener} before insert/update
 * and decrypted after entity load. The database column always contains the
 * ciphertext; the in-memory entity always holds the plaintext.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Encrypted {
}
