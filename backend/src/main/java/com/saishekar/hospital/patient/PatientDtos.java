package com.saishekar.hospital.patient;

import com.saishekar.hospital.model.BloodGroup;
import com.saishekar.hospital.model.Gender;
import com.saishekar.hospital.model.Patient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class PatientDtos {
    private PatientDtos() {}

    public record PatientRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotNull @PastOrPresent LocalDate dateOfBirth,
            @NotNull Gender gender,
            @NotBlank @Size(max = 30) String phone,
            @Email @Size(max = 120) String email,
            @NotNull BloodGroup bloodGroup,
            @Size(max = 300) String address,
            @Size(max = 120) String emergencyContactName,
            @Size(max = 30) String emergencyContactPhone,
            @Size(max = 500) String allergies) {}

    public record PatientResponse(Long id, String firstName, String lastName, LocalDate dateOfBirth,
                                  Gender gender, String phone, String email, BloodGroup bloodGroup,
                                  String address, String emergencyContactName,
                                  String emergencyContactPhone, String allergies) {
        static PatientResponse from(Patient p) {
            return new PatientResponse(p.getId(), p.getFirstName(), p.getLastName(), p.getDateOfBirth(),
                    p.getGender(), p.getPhone(), p.getEmail(), p.getBloodGroup(), p.getAddress(),
                    p.getEmergencyContactName(), p.getEmergencyContactPhone(), p.getAllergies());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size,
                                  long totalElements, int totalPages) {}
}
