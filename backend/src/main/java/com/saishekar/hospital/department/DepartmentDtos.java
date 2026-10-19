package com.saishekar.hospital.department;

import com.saishekar.hospital.model.Department;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DepartmentDtos {
    private DepartmentDtos() {}

    public record DepartmentRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description) {}

    public record DepartmentResponse(Long id, String name, String description, long doctorCount) {
        static DepartmentResponse from(Department d, long doctorCount) {
            return new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), doctorCount);
        }
    }
}
