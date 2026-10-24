package com.saishekar.hospital.repository;

import com.saishekar.hospital.model.Appointment;
import com.saishekar.hospital.model.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // One composable query behind the day view: a date range, a doctor, a
    // patient, and a status all filter the same list instead of each
    // combination growing its own repository method.
    @Query("""
            select a from Appointment a
            where (:doctorId is null or a.doctor.id = :doctorId)
              and (:patientId is null or a.patient.id = :patientId)
              and (:status is null or a.status = :status)
              and (:from is null or a.startsAt >= :from)
              and (:to is null or a.startsAt < :to)
            """)
    Page<Appointment> search(@Param("doctorId") Long doctorId,
                             @Param("patientId") Long patientId,
                             @Param("status") AppointmentStatus status,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);

    // Candidates for the overlap check: appointments for one doctor that
    // start inside a window wide enough to contain any appointment that
    // could overlap the requested slot. The service does the exact
    // interval comparison, because an appointment's end is derived from
    // its duration and cannot be expressed in the query.
    @Query("""
            select a from Appointment a
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and a.startsAt >= :windowStart
              and a.startsAt < :windowEnd
            order by a.startsAt asc
            """)
    List<Appointment> findBlockingCandidates(@Param("doctorId") Long doctorId,
                                             @Param("statuses") Collection<AppointmentStatus> statuses,
                                             @Param("windowStart") Instant windowStart,
                                             @Param("windowEnd") Instant windowEnd);
}
