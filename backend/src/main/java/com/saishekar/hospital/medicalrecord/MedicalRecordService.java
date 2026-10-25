package com.saishekar.hospital.medicalrecord;

import com.saishekar.hospital.common.ApiException;
import com.saishekar.hospital.model.Appointment;
import com.saishekar.hospital.model.AppointmentStatus;
import com.saishekar.hospital.model.Doctor;
import com.saishekar.hospital.model.MedicalRecord;
import com.saishekar.hospital.model.Patient;
import com.saishekar.hospital.model.Prescription;
import com.saishekar.hospital.repository.AppointmentRepository;
import com.saishekar.hospital.repository.DoctorRepository;
import com.saishekar.hospital.repository.MedicalRecordRepository;
import com.saishekar.hospital.repository.PatientRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class MedicalRecordService {

    private final MedicalRecordRepository records;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;

    public MedicalRecordService(MedicalRecordRepository records, PatientRepository patients,
                                DoctorRepository doctors, AppointmentRepository appointments) {
        this.records = records;
        this.patients = patients;
        this.doctors = doctors;
        this.appointments = appointments;
    }

    @Transactional(readOnly = true)
    public MedicalRecordDtos.PageResponse<MedicalRecordDtos.MedicalRecordResponse> list(
            Long patientId, Long doctorId, String query, int page, int size) {
        if (size < 1 || size > 100) {
            throw ApiException.badRequest("size must be between 1 and 100");
        }
        String q = query == null || query.isBlank() ? null : query.trim();
        // A patient's history reads newest first; the id tie-breaker keeps
        // two visits recorded in the same instant in a stable order.
        var pageable = PageRequest.of(page, size, Sort.by("visitAt").descending()
                .and(Sort.by("id").descending()));
        var result = records.search(patientId, doctorId, q, pageable);
        return new MedicalRecordDtos.PageResponse<>(
                result.getContent().stream().map(MedicalRecordDtos.MedicalRecordResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MedicalRecordDtos.MedicalRecordResponse get(Long id) {
        return MedicalRecordDtos.MedicalRecordResponse.from(find(id));
    }

    public MedicalRecordDtos.MedicalRecordResponse create(MedicalRecordDtos.MedicalRecordRequest req) {
        var patient = requirePatient(req.patientId());
        var doctor = requireDoctor(req.doctorId());
        var appointment = resolveAppointment(req, patient, doctor, null);
        requireVisitNotInFuture(req.visitAt());
        var record = new MedicalRecord(patient, doctor, appointment, req.visitAt(),
                req.chiefComplaint(), req.diagnosis(), req.notes(), prescriptionsOf(req));
        return MedicalRecordDtos.MedicalRecordResponse.from(records.save(record));
    }

    public MedicalRecordDtos.MedicalRecordResponse update(Long id,
                                                          MedicalRecordDtos.MedicalRecordRequest req) {
        var record = find(id);
        var patient = requirePatient(req.patientId());
        var doctor = requireDoctor(req.doctorId());
        var appointment = resolveAppointment(req, patient, doctor, id);
        requireVisitNotInFuture(req.visitAt());
        // A correction replaces the record as a whole, prescription lines
        // included: a line removed from the request is a line removed
        // from the chart, not one left behind.
        record.apply(patient, doctor, appointment, req.visitAt(),
                req.chiefComplaint(), req.diagnosis(), req.notes(), prescriptionsOf(req));
        return MedicalRecordDtos.MedicalRecordResponse.from(records.save(record));
    }

    public void delete(Long id) {
        records.delete(find(id));
    }

    private List<Prescription> prescriptionsOf(MedicalRecordDtos.MedicalRecordRequest req) {
        if (req.prescriptions() == null) {
            return List.of();
        }
        return req.prescriptions().stream()
                .map(MedicalRecordDtos.PrescriptionRequest::toPrescription)
                .toList();
    }

    private void requireVisitNotInFuture(Instant visitAt) {
        if (visitAt.isAfter(Instant.now())) {
            throw ApiException.badRequest("visitAt cannot be in the future");
        }
    }

    // The appointment link is optional, but when it is given it has to be
    // the visit this record documents: same patient, same doctor, already
    // completed, and not already carrying another record. Anything else
    // would let a chart entry point at a visit that never happened.
    private Appointment resolveAppointment(MedicalRecordDtos.MedicalRecordRequest req,
                                           Patient patient, Doctor doctor, Long excludeRecordId) {
        if (req.appointmentId() == null) {
            return null;
        }
        var appointment = appointments.findById(req.appointmentId())
                .orElseThrow(() -> ApiException.badRequest(
                        "appointmentId does not reference an existing appointment"));
        if (!appointment.getPatient().getId().equals(patient.getId())
                || !appointment.getDoctor().getId().equals(doctor.getId())) {
            throw ApiException.badRequest(
                    "appointmentId belongs to a different patient or doctor");
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw ApiException.conflict(
                    "Only a completed appointment can be documented; this one is "
                            + appointment.getStatus());
        }
        var existing = records.findByAppointmentId(appointment.getId());
        if (existing.isPresent() && !existing.get().getId().equals(excludeRecordId)) {
            throw ApiException.conflict(
                    "Appointment " + appointment.getId() + " already has a medical record");
        }
        return appointment;
    }

    private Patient requirePatient(Long patientId) {
        return patients.findById(patientId)
                .orElseThrow(() -> ApiException.badRequest("patientId does not reference an existing patient"));
    }

    // Unlike booking, recording a visit does not require an active doctor:
    // a record can document a past visit by a doctor who has since left,
    // and blocking it would falsify the chart's history.
    private Doctor requireDoctor(Long doctorId) {
        return doctors.findById(doctorId)
                .orElseThrow(() -> ApiException.badRequest("doctorId does not reference an existing doctor"));
    }

    private MedicalRecord find(Long id) {
        return records.findById(id).orElseThrow(() -> ApiException.notFound("Medical record"));
    }
}
