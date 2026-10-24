package com.saishekar.hospital.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    // The end is derived (startsAt + duration) rather than stored, so a
    // reschedule can never leave start and end disagreeing with each other.
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Appointment() {
    }

    public Appointment(Patient patient, Doctor doctor, Instant startsAt, int durationMinutes,
                       String reason) {
        apply(patient, doctor, startsAt, durationMinutes, reason);
    }

    public void apply(Patient patient, Doctor doctor, Instant startsAt, int durationMinutes,
                      String reason) {
        this.patient = patient;
        this.doctor = doctor;
        this.startsAt = startsAt;
        this.durationMinutes = durationMinutes;
        this.reason = reason;
    }

    public void changeStatus(AppointmentStatus status) {
        this.status = status;
    }

    public Instant getEndsAt() {
        return startsAt.plusSeconds(durationMinutes * 60L);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public Instant getStartsAt() { return startsAt; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getReason() { return reason; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
