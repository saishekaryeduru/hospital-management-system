package com.saishekar.hospital.department;

import com.saishekar.hospital.common.ApiException;
import com.saishekar.hospital.model.Department;
import com.saishekar.hospital.repository.DepartmentRepository;
import com.saishekar.hospital.repository.DoctorRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository departments;
    private final DoctorRepository doctors;

    public DepartmentService(DepartmentRepository departments, DoctorRepository doctors) {
        this.departments = departments;
        this.doctors = doctors;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDtos.DepartmentResponse> list() {
        // Departments are a short, stable list (a hospital has tens, not
        // thousands), so the directory returns all of them in name order
        // rather than paging.
        return departments.findAll(Sort.by("name").ascending()).stream()
                .map(d -> DepartmentDtos.DepartmentResponse.from(d, doctorCount(d.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentDtos.DepartmentResponse get(Long id) {
        return DepartmentDtos.DepartmentResponse.from(find(id), doctorCount(id));
    }

    public DepartmentDtos.DepartmentResponse create(DepartmentDtos.DepartmentRequest req) {
        requireUniqueName(req.name(), null);
        var department = new Department(req.name().trim(), req.description());
        return DepartmentDtos.DepartmentResponse.from(departments.save(department), 0);
    }

    public DepartmentDtos.DepartmentResponse update(Long id, DepartmentDtos.DepartmentRequest req) {
        var department = find(id);
        requireUniqueName(req.name(), id);
        department.apply(req.name().trim(), req.description());
        return DepartmentDtos.DepartmentResponse.from(departments.save(department), doctorCount(id));
    }

    public void delete(Long id) {
        var department = find(id);
        // Deleting a department that still has doctors would orphan the
        // directory entries appointments will reference. Reassign first.
        if (doctors.existsByDepartmentId(id)) {
            throw ApiException.conflict(
                    "Department still has doctors assigned; reassign or remove them before deleting it");
        }
        departments.delete(department);
    }

    private void requireUniqueName(String name, Long excludeId) {
        departments.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw ApiException.conflict("A department named '" + name.trim() + "' already exists");
            }
        });
    }

    private long doctorCount(Long departmentId) {
        return doctors.countByDepartmentId(departmentId);
    }

    private Department find(Long id) {
        return departments.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
    }
}
