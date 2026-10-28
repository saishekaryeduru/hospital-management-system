package com.saishekar.hospital.dashboard;

import com.saishekar.hospital.appointment.AppointmentDtos;
import com.saishekar.hospital.model.Appointment;
import com.saishekar.hospital.model.AppointmentStatus;
import com.saishekar.hospital.model.Department;
import com.saishekar.hospital.repository.AppointmentRepository;
import com.saishekar.hospital.repository.DepartmentRepository;
import com.saishekar.hospital.repository.DoctorRepository;
import com.saishekar.hospital.repository.PatientRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    // Statuses that represent time a department actually spent or has
    // committed: everything except a booking that fell through.
    private static final Set<AppointmentStatus> BOOKED =
            Set.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CHECKED_IN,
                    AppointmentStatus.COMPLETED);

    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final DepartmentRepository departments;

    public DashboardService(AppointmentRepository appointments, PatientRepository patients,
                            DoctorRepository doctors, DepartmentRepository departments) {
        this.appointments = appointments;
        this.patients = patients;
        this.doctors = doctors;
        this.departments = departments;
    }

    public DashboardDtos.DashboardResponse dashboard(LocalDate date) {
        // The day is the same UTC day the appointment day view uses, so
        // the dashboard and the schedule can never disagree about which
        // appointments belong to a date. No date means today.
        LocalDate day = date == null ? LocalDate.now(ZoneOffset.UTC) : date;
        Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Appointment> todays = appointments.findInRange(from, to);

        // Every status is present in the response, zero included: a
        // client rendering "0 completed" should not have to guess
        // whether the field was omitted or the count is really zero.
        Map<AppointmentStatus, Long> byStatus = new EnumMap<>(AppointmentStatus.class);
        for (AppointmentStatus status : AppointmentStatus.values()) {
            byStatus.put(status, 0L);
        }
        for (Appointment a : todays) {
            byStatus.merge(a.getStatus(), 1L, Long::sum);
        }

        return new DashboardDtos.DashboardResponse(
                day,
                todays.size(),
                byStatus,
                todays.stream().map(AppointmentDtos.AppointmentResponse::from).toList(),
                patients.count(),
                patients.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to),
                departmentLoad(todays));
    }

    private List<DashboardDtos.DepartmentLoad> departmentLoad(List<Appointment> todays) {
        // Group the day's appointments by department once, in memory:
        // the day list is already loaded for the response, and a small
        // hospital's day is a bounded list, not a table scan per
        // department.
        Map<Long, List<Appointment>> byDepartment = new LinkedHashMap<>();
        for (Appointment a : todays) {
            byDepartment.computeIfAbsent(a.getDoctor().getDepartment().getId(),
                    id -> new ArrayList<>()).add(a);
        }

        List<DashboardDtos.DepartmentLoad> load = new ArrayList<>();
        for (Department department : departments.findAll(Sort.by("name").ascending())) {
            List<Appointment> mine = byDepartment.getOrDefault(department.getId(), List.of());
            long completed = mine.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
            long bookedMinutes = mine.stream()
                    .filter(a -> BOOKED.contains(a.getStatus()))
                    .mapToLong(Appointment::getDurationMinutes).sum();
            load.add(new DashboardDtos.DepartmentLoad(
                    department.getId(), department.getName(),
                    doctors.countByDepartmentId(department.getId()),
                    doctors.countByDepartmentIdAndActiveTrue(department.getId()),
                    mine.size(), completed, bookedMinutes));
        }
        return load;
    }
}
