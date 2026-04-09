package com.rentwrangler.event;

import com.rentwrangler.annotation.Encrypted;
import com.rentwrangler.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Hibernate event listener that transparently encrypts and decrypts
 * fields annotated with {@link Encrypted}.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@link PreInsertEventListener} / {@link PreUpdateEventListener} —
 *       encrypts the value in Hibernate's state array before the SQL is issued.
 *       The entity object in memory is left unchanged so callers always see plaintext.</li>
 *   <li>{@link PostLoadEventListener} — decrypts annotated fields on the entity
 *       object after Hibernate has hydrated it from the result set.</li>
 * </ul>
 *
 * <p>Registered programmatically via {@link com.rentwrangler.config.HibernateListenerConfig}
 * so that Spring can inject dependencies into this bean.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncryptionEventListener
        implements PreInsertEventListener, PreUpdateEventListener, PostLoadEventListener {

    private final EncryptionService encryptionService;

    // -----------------------------------------------------------------------
    // Pre-insert: encrypt annotated fields in the state array
    // -----------------------------------------------------------------------

    @Override
    public boolean onPreInsert(PreInsertEvent event) {
        processState(event.getEntity(), event.getState(), event.getPersister().getPropertyNames());
        return false; // returning true would veto the insert
    }

    // -----------------------------------------------------------------------
    // Pre-update: encrypt annotated fields in the state array
    // -----------------------------------------------------------------------

    @Override
    public boolean onPreUpdate(PreUpdateEvent event) {
        processState(event.getEntity(), event.getState(), event.getPersister().getPropertyNames());
        return false;
    }

    // -----------------------------------------------------------------------
    // Post-load: decrypt annotated fields directly on the entity
    // -----------------------------------------------------------------------

    @Override
    public void onPostLoad(PostLoadEvent event) {
        decryptEntity(event.getEntity());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Iterates over the Hibernate property state array and replaces any
     * {@link Encrypted}-annotated String value with its ciphertext.
     *
     * <p>Operating on the state array (not the entity object) ensures that
     * the entity in memory retains the plaintext for the duration of the request.
     */
    private void processState(Object entity, Object[] state, String[] propertyNames) {
        Arrays.stream(entity.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Encrypted.class))
                .filter(field -> field.getType() == String.class)
                .forEach(field -> {
                    for (int i = 0; i < propertyNames.length; i++) {
                        if (propertyNames[i].equals(field.getName())) {
                            String plaintext = (String) state[i];
                            if (plaintext != null) {
                                log.debug("Encrypting field '{}' on {}", field.getName(),
                                        entity.getClass().getSimpleName());
                                state[i] = encryptionService.encrypt(plaintext);
                            }
                            break;
                        }
                    }
                });
    }

    /**
     * Decrypts {@link Encrypted}-annotated String fields directly on the entity
     * object after it has been loaded from the database.
     */
    private void decryptEntity(Object entity) {
        Arrays.stream(entity.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Encrypted.class))
                .filter(field -> field.getType() == String.class)
                .forEach(field -> {
                    try {
                        field.setAccessible(true);
                        String ciphertext = (String) field.get(entity);
                        if (ciphertext != null) {
                            log.debug("Decrypting field '{}' on {}", field.getName(),
                                    entity.getClass().getSimpleName());
                            field.set(entity, encryptionService.decrypt(ciphertext));
                        }
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(
                                "Could not access encrypted field: " + field.getName(), e);
                    }
                });
    }
}
