package com.saishekar.hospital.patient;

import com.saishekar.hospital.common.ApiException;
import com.saishekar.hospital.model.Patient;
import com.saishekar.hospital.repository.PatientRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PatientService {

    private final PatientRepository patients;

    public PatientService(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public PatientDtos.PageResponse<PatientDtos.PatientResponse> list(String q, int page, int size) {
        if (size < 1 || size > 100) {
            throw ApiException.badRequest("size must be between 1 and 100");
        }
        // Stable ordering matters for paging: last name, then first name,
        // then id as the final tie-breaker for patients who share a name.
        var pageable = PageRequest.of(page, size, Sort.by("lastName").ascending()
                .and(Sort.by("firstName").ascending()).and(Sort.by("id").ascending()));
        var result = (q == null || q.isBlank()) ? patients.findAll(pageable) : patients.search(q.trim(), pageable);
        return new PatientDtos.PageResponse<>(
                result.getContent().stream().map(PatientDtos.PatientResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PatientDtos.PatientResponse get(Long id) {
        return PatientDtos.PatientResponse.from(find(id));
    }

    public PatientDtos.PatientResponse create(PatientDtos.PatientRequest req) {
        var patient = new Patient(req.firstName(), req.lastName(), req.dateOfBirth(), req.gender(),
                req.phone(), req.email(), req.bloodGroup(), req.address(),
                req.emergencyContactName(), req.emergencyContactPhone(), req.allergies());
        return PatientDtos.PatientResponse.from(patients.save(patient));
    }

    public PatientDtos.PatientResponse update(Long id, PatientDtos.PatientRequest req) {
        var patient = find(id);
        patient.apply(req.firstName(), req.lastName(), req.dateOfBirth(), req.gender(),
                req.phone(), req.email(), req.bloodGroup(), req.address(),
                req.emergencyContactName(), req.emergencyContactPhone(), req.allergies());
        return PatientDtos.PatientResponse.from(patients.save(patient));
    }

    public void delete(Long id) {
        patients.delete(find(id));
    }

    private Patient find(Long id) {
        return patients.findById(id).orElseThrow(() -> ApiException.notFound("Patient"));
    }
}
