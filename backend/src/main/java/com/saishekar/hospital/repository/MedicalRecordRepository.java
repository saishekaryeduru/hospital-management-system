package com.saishekar.hospital.repository;

import com.saishekar.hospital.model.MedicalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    // One composable query behind the record list: a patient's history,
    // a doctor's visits, and a text search over the presenting complaint
    // and the diagnosis all filter the same list.
    @Query("""
            select r from MedicalRecord r
            where (:patientId is null or r.patient.id = :patientId)
              and (:doctorId is null or r.doctor.id = :doctorId)
              and (:q is null
                   or lower(r.diagnosis) like lower(concat('%', :q, '%'))
                   or lower(r.chiefComplaint) like lower(concat('%', :q, '%')))
            """)
    Page<MedicalRecord> search(@Param("patientId") Long patientId,
                               @Param("doctorId") Long doctorId,
                               @Param("q") String q,
                               Pageable pageable);

    Optional<MedicalRecord> findByAppointmentId(Long appointmentId);
}
