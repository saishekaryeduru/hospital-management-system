package com.saishekar.hospital.appointment;

import com.saishekar.hospital.model.Appointment;
import com.saishekar.hospital.model.AppointmentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AppointmentDtos {
    private AppointmentDtos() {}

    public record AppointmentRequest(
            @NotNull Long patientId,
            @NotNull Long doctorId,
            @NotNull Instant startsAt,
            @Min(5) @Max(240) Integer durationMinutes,
            @Size(max = 500) String reason) {}

    public record StatusRequest(@NotNull AppointmentStatus status) {}

    public record AppointmentResponse(Long id, Long patientId, String patientName,
                                      Long doctorId, String doctorName,
                                      Long departmentId, String departmentName,
                                      Instant startsAt, int durationMinutes, Instant endsAt,
                                      String reason, AppointmentStatus status) {
        static AppointmentResponse from(Appointment a) {
            var patient = a.getPatient();
            var doctor = a.getDoctor();
            return new AppointmentResponse(a.getId(),
                    patient.getId(), patient.getFirstName() + " " + patient.getLastName(),
                    doctor.getId(), doctor.getFirstName() + " " + doctor.getLastName(),
                    doctor.getDepartment().getId(), doctor.getDepartment().getName(),
                    a.getStartsAt(), a.getDurationMinutes(), a.getEndsAt(),
                    a.getReason(), a.getStatus());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size,
                                  long totalElements, int totalPages) {}
}
