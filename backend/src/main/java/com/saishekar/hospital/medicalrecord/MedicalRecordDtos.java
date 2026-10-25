package com.saishekar.hospital.medicalrecord;

import com.saishekar.hospital.model.MedicalRecord;
import com.saishekar.hospital.model.Prescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class MedicalRecordDtos {
    private MedicalRecordDtos() {}

    public record PrescriptionRequest(
            @NotBlank @Size(max = 120) String medication,
            @NotBlank @Size(max = 80) String dosage,
            @NotBlank @Size(max = 80) String frequency,
            @Min(1) @Max(365) Integer durationDays,
            @Size(max = 300) String instructions) {

        Prescription toPrescription() {
            return new Prescription(medication, dosage, frequency, durationDays, instructions);
        }
    }

    public record MedicalRecordRequest(
            @NotNull Long patientId,
            @NotNull Long doctorId,
            Long appointmentId,
            @NotNull Instant visitAt,
            @NotBlank @Size(max = 500) String chiefComplaint,
            @NotBlank @Size(max = 1000) String diagnosis,
            @Size(max = 2000) String notes,
            @Valid @Size(max = 20) List<PrescriptionRequest> prescriptions) {}

    public record PrescriptionResponse(String medication, String dosage, String frequency,
                                       Integer durationDays, String instructions) {
        static PrescriptionResponse from(Prescription p) {
            return new PrescriptionResponse(p.getMedication(), p.getDosage(), p.getFrequency(),
                    p.getDurationDays(), p.getInstructions());
        }
    }

    public record MedicalRecordResponse(Long id, Long patientId, String patientName,
                                        Long doctorId, String doctorName,
                                        Long departmentId, String departmentName,
                                        Long appointmentId, Instant visitAt,
                                        String chiefComplaint, String diagnosis, String notes,
                                        List<PrescriptionResponse> prescriptions) {
        static MedicalRecordResponse from(MedicalRecord r) {
            var patient = r.getPatient();
            var doctor = r.getDoctor();
            return new MedicalRecordResponse(r.getId(),
                    patient.getId(), patient.getFirstName() + " " + patient.getLastName(),
                    doctor.getId(), doctor.getFirstName() + " " + doctor.getLastName(),
                    doctor.getDepartment().getId(), doctor.getDepartment().getName(),
                    r.getAppointment() == null ? null : r.getAppointment().getId(),
                    r.getVisitAt(), r.getChiefComplaint(), r.getDiagnosis(), r.getNotes(),
                    r.getPrescriptions().stream().map(PrescriptionResponse::from).toList());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size,
                                  long totalElements, int totalPages) {}
}
