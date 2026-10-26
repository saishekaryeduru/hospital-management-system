package com.saishekar.hospital.billing;

import com.saishekar.hospital.common.ApiException;
import com.saishekar.hospital.model.Appointment;
import com.saishekar.hospital.model.AppointmentStatus;
import com.saishekar.hospital.model.ChargeItem;
import com.saishekar.hospital.model.Invoice;
import com.saishekar.hospital.model.InvoiceStatus;
import com.saishekar.hospital.model.Patient;
import com.saishekar.hospital.repository.AppointmentRepository;
import com.saishekar.hospital.repository.InvoiceRepository;
import com.saishekar.hospital.repository.PatientRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class InvoiceService {

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> TRANSITIONS =
            new EnumMap<>(InvoiceStatus.class);
    static {
        // Payment settles an invoice and voiding cancels it; neither
        // can be undone through the API, because unwinding either one
        // in a real billing office is a new document (a refund, a
        // reissue), not an edit to this one.
        TRANSITIONS.put(InvoiceStatus.UNPAID, Set.of(InvoiceStatus.PAID, InvoiceStatus.VOID));
        TRANSITIONS.put(InvoiceStatus.PAID, Set.of());
        TRANSITIONS.put(InvoiceStatus.VOID, Set.of());
    }

    private final InvoiceRepository invoices;
    private final PatientRepository patients;
    private final AppointmentRepository appointments;

    public InvoiceService(InvoiceRepository invoices, PatientRepository patients,
                          AppointmentRepository appointments) {
        this.invoices = invoices;
        this.patients = patients;
        this.appointments = appointments;
    }

    @Transactional(readOnly = true)
    public InvoiceDtos.PageResponse<InvoiceDtos.InvoiceResponse> list(
            Long patientId, InvoiceStatus status, int page, int size) {
        if (size < 1 || size > 100) {
            throw ApiException.badRequest("size must be between 1 and 100");
        }
        // Newest issued first, like a billing queue; the id tie-breaker
        // keeps two invoices issued in the same instant in stable order.
        var pageable = PageRequest.of(page, size, Sort.by("issuedAt").descending()
                .and(Sort.by("id").descending()));
        var result = invoices.search(patientId, status, pageable);
        return new InvoiceDtos.PageResponse<>(
                result.getContent().stream().map(InvoiceDtos.InvoiceResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public InvoiceDtos.InvoiceResponse get(Long id) {
        return InvoiceDtos.InvoiceResponse.from(find(id));
    }

    public InvoiceDtos.InvoiceResponse create(InvoiceDtos.InvoiceRequest req) {
        var patient = requirePatient(req.patientId());
        var appointment = resolveAppointment(req, patient, null);
        requireIssuedNotInFuture(req.issuedAt());
        var invoice = new Invoice(patient, appointment, req.issuedAt(), req.notes(), linesOf(req));
        return InvoiceDtos.InvoiceResponse.from(invoices.save(invoice));
    }

    public InvoiceDtos.InvoiceResponse update(Long id, InvoiceDtos.InvoiceRequest req) {
        var invoice = find(id);
        requireUnpaid(invoice, "edited");
        var patient = requirePatient(req.patientId());
        var appointment = resolveAppointment(req, patient, id);
        requireIssuedNotInFuture(req.issuedAt());
        invoice.apply(patient, appointment, req.issuedAt(), req.notes(), linesOf(req));
        return InvoiceDtos.InvoiceResponse.from(invoices.save(invoice));
    }

    public InvoiceDtos.InvoiceResponse changeStatus(Long id, InvoiceStatus target) {
        var invoice = find(id);
        if (!TRANSITIONS.get(invoice.getStatus()).contains(target)) {
            throw ApiException.conflict(
                    "Invoice cannot move from " + invoice.getStatus() + " to " + target);
        }
        invoice.changeStatus(target);
        return InvoiceDtos.InvoiceResponse.from(invoices.save(invoice));
    }

    public void delete(Long id) {
        var invoice = find(id);
        requireUnpaid(invoice, "deleted");
        invoices.delete(invoice);
    }

    // A settled or voided invoice is a financial record: it is not
    // edited or removed after the fact. Corrections happen while it is
    // still unpaid; after that, the audit trail is the point.
    private void requireUnpaid(Invoice invoice, String action) {
        if (invoice.getStatus() != InvoiceStatus.UNPAID) {
            throw ApiException.conflict(
                    "Only an unpaid invoice can be " + action + "; this one is " + invoice.getStatus());
        }
    }

    private List<ChargeItem> linesOf(InvoiceDtos.InvoiceRequest req) {
        return req.lines().stream().map(InvoiceDtos.ChargeItemRequest::toChargeItem).toList();
    }

    private void requireIssuedNotInFuture(Instant issuedAt) {
        if (issuedAt.isAfter(Instant.now())) {
            throw ApiException.badRequest("issuedAt cannot be in the future");
        }
    }

    // The appointment link follows the same contract as medical
    // records: when given, it must be a completed visit for the same
    // patient, and one visit carries at most one invoice.
    private Appointment resolveAppointment(InvoiceDtos.InvoiceRequest req,
                                           Patient patient, Long excludeInvoiceId) {
        if (req.appointmentId() == null) {
            return null;
        }
        var appointment = appointments.findById(req.appointmentId())
                .orElseThrow(() -> ApiException.badRequest(
                        "appointmentId does not reference an existing appointment"));
        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw ApiException.badRequest("appointmentId belongs to a different patient");
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw ApiException.conflict(
                    "Only a completed appointment can be billed; this one is "
                            + appointment.getStatus());
        }
        var existing = invoices.findByAppointmentId(appointment.getId());
        if (existing.isPresent() && !existing.get().getId().equals(excludeInvoiceId)) {
            throw ApiException.conflict(
                    "Appointment " + appointment.getId() + " already has an invoice");
        }
        return appointment;
    }

    private Patient requirePatient(Long patientId) {
        return patients.findById(patientId)
                .orElseThrow(() -> ApiException.badRequest("patientId does not reference an existing patient"));
    }

    private Invoice find(Long id) {
        return invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice"));
    }
}
