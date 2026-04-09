package com.rentwrangler.repository;

import com.rentwrangler.domain.entity.Property;
import com.rentwrangler.domain.enums.PropertyStatus;
import com.rentwrangler.domain.enums.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    Page<Property> findByStatus(PropertyStatus status, Pageable pageable);

    Page<Property> findByPropertyType(PropertyType propertyType, Pageable pageable);

    Page<Property> findByStatusAndPropertyType(PropertyStatus status, PropertyType type, Pageable pageable);

    Page<Property> findByCityAndState(String city, String state, Pageable pageable);

    Page<Property> findByZipCode(String zipCode, Pageable pageable);

    boolean existsByStreetAddressAndCityAndState(String streetAddress, String city, String state);

    List<Property> findByStatus(PropertyStatus status);
}
