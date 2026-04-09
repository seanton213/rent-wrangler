package com.rentwrangler.config;

import com.rentwrangler.event.EncryptionEventListener;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.PersistenceUnit;
import lombok.RequiredArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.EntityManagerFactory;

/**
 * Registers the {@link EncryptionEventListener} with Hibernate's
 * {@link EventListenerRegistry}.
 *
 * <p>We cannot rely on {@code @Component} alone because Hibernate instantiates
 * its listeners internally. Instead, we unwrap the {@link SessionFactory} after
 * Spring has created the {@link EntityManagerFactory} and append our Spring-managed
 * bean to the relevant event types.
 */
@Configuration
@RequiredArgsConstructor
public class HibernateListenerConfig {

    private final EncryptionEventListener encryptionEventListener;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @PostConstruct
    public void registerListeners() {
        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
        EventListenerRegistry registry = sessionFactory
                .getServiceRegistry()
                .getService(EventListenerRegistry.class);

        registry.appendListeners(EventType.PRE_INSERT, encryptionEventListener);
        registry.appendListeners(EventType.PRE_UPDATE,  encryptionEventListener);
        registry.appendListeners(EventType.POST_LOAD,   encryptionEventListener);
    }
}
