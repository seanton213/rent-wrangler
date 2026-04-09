package com.rentwrangler.persistence;

import com.rentwrangler.domain.entity.Property;
import com.rentwrangler.domain.enums.PropertyStatus;
import com.rentwrangler.domain.enums.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Persistence facade for {@link Property}.
 *
 * <p>All business logic accesses the database through this interface.
 * Simple CRUD is delegated to the underlying repository; complex queries
 * are implemented using {@code @PersistenceContext} in the implementation.
 */
public interface PropertyPersistenceService {

    Property save(Property property);

    Optional<Property> findById(Long id);

    Page<Property> findAll(Pageable pageable);

    Page<Property> findByStatus(PropertyStatus status, Pageable pageable);

    Page<Property> findByTypeAndStatus(PropertyType type, PropertyStatus status, Pageable pageable);

    /** Vacancy summary: returns [{propertyId, propertyName, vacantUnits, totalUnits}] */
    Page<Property> findWithVacantUnits(Pageable pageable);

    boolean existsByAddress(String streetAddress, String city, String state);

    void deleteById(Long id);
}
