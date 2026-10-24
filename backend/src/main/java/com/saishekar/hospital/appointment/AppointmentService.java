package com.saishekar.hospital.appointment;

import com.saishekar.hospital.common.ApiException;
import com.saishekar.hospital.model.Appointment;
import com.saishekar.hospital.model.AppointmentStatus;
import com.saishekar.hospital.model.Doctor;
import com.saishekar.hospital.model.Patient;
import com.saishekar.hospital.repository.AppointmentRepository;
import com.saishekar.hospital.repository.DoctorRepository;
import com.saishekar.hospital.repository.PatientRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class AppointmentService {

    static final int DEFAULT_DURATION_MINUTES = 30;
    // Must match the @Max on AppointmentRequest: the overlap window below
    // looks this far behind the requested start for a long appointment
    // that began earlier and is still running.
    static final int MAX_DURATION_MINUTES = 240;

    // Only live bookings block a calendar. A cancelled or no-show
    // appointment frees its slot, and a completed one is in the past by
    // the time anything could be booked over it.
    private static final Set<AppointmentStatus> BLOCKING =
            Set.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CHECKED_IN);

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> TRANSITIONS =
            new EnumMap<>(AppointmentStatus.class);
    static {
        // A visit is checked in before it can be completed: completing a
        // booking the patient never arrived for would write a visit that
        // did not happen into the record the billing module will read.
        TRANSITIONS.put(AppointmentStatus.SCHEDULED,
                Set.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW));
        TRANSITIONS.put(AppointmentStatus.CHECKED_IN,
                Set.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW));
        TRANSITIONS.put(AppointmentStatus.COMPLETED, Set.of());
        TRANSITIONS.put(AppointmentStatus.CANCELLED, Set.of());
        TRANSITIONS.put(AppointmentStatus.NO_SHOW, Set.of());
    }

    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final DoctorRepository doctors;

    public AppointmentService(AppointmentRepository appointments, PatientRepository patients,
                              DoctorRepository doctors) {
        this.appointments = appointments;
        this.patients = patients;
        this.doctors = doctors;
    }

    @Transactional(readOnly = true)
    public AppointmentDtos.PageResponse<AppointmentDtos.AppointmentResponse> list(
            LocalDate date, Long doctorId, Long patientId, AppointmentStatus status,
            int page, int size) {
        if (size < 1 || size > 100) {
            throw ApiException.badRequest("size must be between 1 and 100");
        }
        // A day view is a half-open UTC range, so an appointment at exactly
        // midnight belongs to exactly one day.
        Instant from = date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var pageable = PageRequest.of(page, size, Sort.by("startsAt").ascending()
                .and(Sort.by("id").ascending()));
        var result = appointments.search(doctorId, patientId, status, from, to, pageable);
        return new AppointmentDtos.PageResponse<>(
                result.getContent().stream().map(AppointmentDtos.AppointmentResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AppointmentDtos.AppointmentResponse get(Long id) {
        return AppointmentDtos.AppointmentResponse.from(find(id));
    }

    public AppointmentDtos.AppointmentResponse create(AppointmentDtos.AppointmentRequest req) {
        var patient = requirePatient(req.patientId());
        var doctor = requireBookableDoctor(req.doctorId());
        int duration = durationOf(req);
        requireFuture(req.startsAt());
        requireNoOverlap(doctor, req.startsAt(), duration, null);
        var appointment = new Appointment(patient, doctor, req.startsAt(), duration, req.reason());
        return AppointmentDtos.AppointmentResponse.from(appointments.save(appointment));
    }

    public AppointmentDtos.AppointmentResponse update(Long id, AppointmentDtos.AppointmentRequest req) {
        var appointment = find(id);
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw ApiException.conflict(
                    "Only a scheduled appointment can be edited; this one is " + appointment.getStatus());
        }
        var patient = requirePatient(req.patientId());
        var doctor = requireBookableDoctor(req.doctorId());
        int duration = durationOf(req);
        requireFuture(req.startsAt());
        requireNoOverlap(doctor, req.startsAt(), duration, id);
        appointment.apply(patient, doctor, req.startsAt(), duration, req.reason());
        return AppointmentDtos.AppointmentResponse.from(appointments.save(appointment));
    }

    public AppointmentDtos.AppointmentResponse changeStatus(Long id, AppointmentStatus target) {
        var appointment = find(id);
        if (!TRANSITIONS.get(appointment.getStatus()).contains(target)) {
            throw ApiException.conflict(
                    "Appointment cannot move from " + appointment.getStatus() + " to " + target);
        }
        appointment.changeStatus(target);
        return AppointmentDtos.AppointmentResponse.from(appointments.save(appointment));
    }

    public void delete(Long id) {
        appointments.delete(find(id));
    }

    private int durationOf(AppointmentDtos.AppointmentRequest req) {
        return req.durationMinutes() == null ? DEFAULT_DURATION_MINUTES : req.durationMinutes();
    }

    private void requireFuture(Instant startsAt) {
        if (!startsAt.isAfter(Instant.now())) {
            throw ApiException.badRequest("startsAt must be in the future");
        }
    }

    private void requireNoOverlap(Doctor doctor, Instant startsAt, int durationMinutes, Long excludeId) {
        Instant endsAt = startsAt.plusSeconds(durationMinutes * 60L);
        Instant windowStart = startsAt.minusSeconds(MAX_DURATION_MINUTES * 60L);
        List<Appointment> candidates =
                appointments.findBlockingCandidates(doctor.getId(), BLOCKING, windowStart, endsAt);
        for (Appointment other : candidates) {
            if (excludeId != null && other.getId().equals(excludeId)) {
                continue;
            }
            // Half-open intervals: an appointment that starts exactly when
            // another ends (back-to-back bookings) does not overlap it.
            if (other.getStartsAt().isBefore(endsAt) && startsAt.isBefore(other.getEndsAt())) {
                throw ApiException.conflict("Dr. " + doctor.getFirstName() + " " + doctor.getLastName()
                        + " already has an appointment from " + other.getStartsAt()
                        + " to " + other.getEndsAt());
            }
        }
    }

    private Patient requirePatient(Long patientId) {
        return patients.findById(patientId)
                .orElseThrow(() -> ApiException.badRequest("patientId does not reference an existing patient"));
    }

    private Doctor requireBookableDoctor(Long doctorId) {
        var doctor = doctors.findById(doctorId)
                .orElseThrow(() -> ApiException.badRequest("doctorId does not reference an existing doctor"));
        if (!doctor.isActive()) {
            throw ApiException.conflict("Dr. " + doctor.getFirstName() + " " + doctor.getLastName()
                    + " is inactive and cannot take new appointments");
        }
        return doctor;
    }

    private Appointment find(Long id) {
        return appointments.findById(id).orElseThrow(() -> ApiException.notFound("Appointment"));
    }
}
