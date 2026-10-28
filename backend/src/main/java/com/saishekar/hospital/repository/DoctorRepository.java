package com.saishekar.hospital.repository;

import com.saishekar.hospital.model.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    java.util.Optional<Doctor> findByEmailIgnoreCase(String email);

    boolean existsByDepartmentId(Long departmentId);

    long countByDepartmentId(Long departmentId);

    long countByDepartmentIdAndActiveTrue(Long departmentId);

    Page<Doctor> findByDepartmentId(Long departmentId, Pageable pageable);

    // Directory lookup mirrors the patient search: one box matches a name
    // fragment or a specialization, so "cardio" finds Cardiology staff
    // without a separate filter control.
    @Query("""
            select d from Doctor d
            where lower(d.firstName) like lower(concat('%', :q, '%'))
               or lower(d.lastName) like lower(concat('%', :q, '%'))
               or lower(d.specialization) like lower(concat('%', :q, '%'))
            """)
    Page<Doctor> search(@Param("q") String q, Pageable pageable);

    @Query("""
            select d from Doctor d
            where d.department.id = :departmentId
              and (lower(d.firstName) like lower(concat('%', :q, '%'))
               or lower(d.lastName) like lower(concat('%', :q, '%'))
               or lower(d.specialization) like lower(concat('%', :q, '%')))
            """)
    Page<Doctor> searchInDepartment(@Param("q") String q, @Param("departmentId") Long departmentId,
                                    Pageable pageable);
}
