package com.saishekar.hospital.doctor;

import com.saishekar.hospital.common.ApiException;
import com.saishekar.hospital.model.Department;
import com.saishekar.hospital.model.Doctor;
import com.saishekar.hospital.repository.DepartmentRepository;
import com.saishekar.hospital.repository.DoctorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DoctorService {

    private final DoctorRepository doctors;
    private final DepartmentRepository departments;

    public DoctorService(DoctorRepository doctors, DepartmentRepository departments) {
        this.doctors = doctors;
        this.departments = departments;
    }

    @Transactional(readOnly = true)
    public DoctorDtos.PageResponse<DoctorDtos.DoctorResponse> list(String q, Long departmentId,
                                                                   int page, int size) {
        if (size < 1 || size > 100) {
            throw ApiException.badRequest("size must be between 1 and 100");
        }
        // Same stable ordering as the patient list: last name, first name,
        // then id so directory pages never repeat or skip an entry.
        var pageable = PageRequest.of(page, size, Sort.by("lastName").ascending()
                .and(Sort.by("firstName").ascending()).and(Sort.by("id").ascending()));
        boolean hasQuery = q != null && !q.isBlank();
        var result = departmentId == null
                ? (hasQuery ? doctors.search(q.trim(), pageable) : doctors.findAll(pageable))
                : (hasQuery ? doctors.searchInDepartment(q.trim(), departmentId, pageable)
                            : doctors.findByDepartmentId(departmentId, pageable));
        return new DoctorDtos.PageResponse<>(
                result.getContent().stream().map(DoctorDtos.DoctorResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DoctorDtos.DoctorResponse get(Long id) {
        return DoctorDtos.DoctorResponse.from(find(id));
    }

    public DoctorDtos.DoctorResponse create(DoctorDtos.DoctorRequest req) {
        var department = requireDepartment(req.departmentId());
        requireUniqueEmail(req.email(), null);
        var doctor = new Doctor(req.firstName(), req.lastName(), req.specialization(), department,
                req.phone(), req.email(), req.active() == null || req.active());
        return DoctorDtos.DoctorResponse.from(doctors.save(doctor));
    }

    public DoctorDtos.DoctorResponse update(Long id, DoctorDtos.DoctorRequest req) {
        var doctor = find(id);
        var department = requireDepartment(req.departmentId());
        requireUniqueEmail(req.email(), id);
        doctor.apply(req.firstName(), req.lastName(), req.specialization(), department,
                req.phone(), req.email(), req.active() == null || req.active());
        return DoctorDtos.DoctorResponse.from(doctors.save(doctor));
    }

    public void delete(Long id) {
        doctors.delete(find(id));
    }

    private Department requireDepartment(Long departmentId) {
        return departments.findById(departmentId)
                .orElseThrow(() -> ApiException.badRequest("departmentId does not reference an existing department"));
    }

    private void requireUniqueEmail(String email, Long excludeId) {
        // Email is the directory's contact key; a duplicate would make
        // referral and schedule mail ambiguous.
        doctors.findByEmailIgnoreCase(email).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw ApiException.conflict("A doctor with email '" + email + "' already exists");
            }
        });
    }

    private Doctor find(Long id) {
        return doctors.findById(id).orElseThrow(() -> ApiException.notFound("Doctor"));
    }
}
