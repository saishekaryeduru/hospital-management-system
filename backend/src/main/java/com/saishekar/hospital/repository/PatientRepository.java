package com.saishekar.hospital.repository;

import com.saishekar.hospital.model.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    // One search box covers the ways front-desk staff actually look a
    // patient up: by name fragment or by phone digits.
    @Query("""
            select p from Patient p
            where lower(p.firstName) like lower(concat('%', :q, '%'))
               or lower(p.lastName) like lower(concat('%', :q, '%'))
               or p.phone like concat('%', :q, '%')
            """)
    Page<Patient> search(@Param("q") String q, Pageable pageable);

    // Registrations in a half-open instant range; the dashboard passes
    // one UTC day, matching the day boundary appointments use.
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(java.time.Instant from, java.time.Instant to);
}
