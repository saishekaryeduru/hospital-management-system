package com.saishekar.hospital.repository;

import com.saishekar.hospital.model.Invoice;
import com.saishekar.hospital.model.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // One composable query behind the billing list: a patient's
    // invoices and the unpaid queue are the same list with different
    // filters, so both read through here.
    @Query("""
            select i from Invoice i
            where (:patientId is null or i.patient.id = :patientId)
              and (:status is null or i.status = :status)
            """)
    Page<Invoice> search(@Param("patientId") Long patientId,
                         @Param("status") InvoiceStatus status,
                         Pageable pageable);

    Optional<Invoice> findByAppointmentId(Long appointmentId);
}
