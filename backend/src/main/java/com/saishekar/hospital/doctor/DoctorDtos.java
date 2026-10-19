package com.saishekar.hospital.doctor;

import com.saishekar.hospital.model.Doctor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class DoctorDtos {
    private DoctorDtos() {}

    public record DoctorRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Size(max = 120) String specialization,
            @NotNull Long departmentId,
            @NotBlank @Size(max = 30) String phone,
            @NotBlank @Email @Size(max = 120) String email,
            Boolean active) {}

    public record DoctorResponse(Long id, String firstName, String lastName, String specialization,
                                 Long departmentId, String departmentName, String phone, String email,
                                 boolean active) {
        static DoctorResponse from(Doctor d) {
            return new DoctorResponse(d.getId(), d.getFirstName(), d.getLastName(), d.getSpecialization(),
                    d.getDepartment().getId(), d.getDepartment().getName(), d.getPhone(), d.getEmail(),
                    d.isActive());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size,
                                  long totalElements, int totalPages) {}
}
