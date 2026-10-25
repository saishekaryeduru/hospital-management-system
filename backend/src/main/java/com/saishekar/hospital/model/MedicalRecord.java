package com.saishekar.hospital.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "medical_records")
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    // A record documents a visit. When the visit was booked, the record
    // links back to that appointment (unique: one record per visit);
    // walk-ins and referrals are recorded without one. The link is kept
    // on the record rather than the appointment because the record is
    // the document -- the appointment is just its scheduling history.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", unique = true)
    private Appointment appointment;

    @Column(name = "visit_at", nullable = false)
    private Instant visitAt;

    @Column(name = "chief_complaint", nullable = false, length = 500)
    private String chiefComplaint;

    @Column(nullable = false, length = 1000)
    private String diagnosis;

    @Column(length = 2000)
    private String notes;

    // Prescription lines keep the order they were written in; the list
    // is replaced as a whole when the record is corrected.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "medical_record_prescriptions",
            joinColumns = @JoinColumn(name = "medical_record_id"))
    @OrderColumn(name = "line_order")
    private List<Prescription> prescriptions = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MedicalRecord() {
    }

    public MedicalRecord(Patient patient, Doctor doctor, Appointment appointment, Instant visitAt,
                         String chiefComplaint, String diagnosis, String notes,
                         List<Prescription> prescriptions) {
        apply(patient, doctor, appointment, visitAt, chiefComplaint, diagnosis, notes, prescriptions);
    }

    public void apply(Patient patient, Doctor doctor, Appointment appointment, Instant visitAt,
                      String chiefComplaint, String diagnosis, String notes,
                      List<Prescription> prescriptions) {
        this.patient = patient;
        this.doctor = doctor;
        this.appointment = appointment;
        this.visitAt = visitAt;
        this.chiefComplaint = chiefComplaint;
        this.diagnosis = diagnosis;
        this.notes = notes;
        this.prescriptions = new ArrayList<>(prescriptions);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public Appointment getAppointment() { return appointment; }
    public Instant getVisitAt() { return visitAt; }
    public String getChiefComplaint() { return chiefComplaint; }
    public String getDiagnosis() { return diagnosis; }
    public String getNotes() { return notes; }
    public List<Prescription> getPrescriptions() { return prescriptions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
