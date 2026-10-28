package com.saishekar.hospital.dashboard;

import com.saishekar.hospital.appointment.AppointmentDtos;
import com.saishekar.hospital.model.AppointmentStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DashboardDtos {
    private DashboardDtos() {}

    // One department's load for the day: how many doctors it has, how
    // many of them can take bookings, and how much of the day its
    // appointments account for. bookedMinutes counts live and completed
    // visits only -- a cancelled or no-show slot is not load, it is a
    // gap the desk can fill again.
    public record DepartmentLoad(Long departmentId, String departmentName,
                                 long doctorCount, long activeDoctorCount,
                                 long appointments, long completed,
                                 long bookedMinutes) {}

    // Counts are explicit fields, not values derived from the lists, so
    // what the client renders can never disagree with what it counts.
    public record DashboardResponse(
            LocalDate date,
            long appointmentsTotal,
            Map<AppointmentStatus, Long> appointmentsByStatus,
            List<AppointmentDtos.AppointmentResponse> appointments,
            long patientsTotal,
            long patientsRegistered,
            List<DepartmentLoad> departmentLoad) {}
}
