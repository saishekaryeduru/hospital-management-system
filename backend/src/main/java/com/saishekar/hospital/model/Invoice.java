package com.saishekar.hospital.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    // An invoice normally bills a completed visit, and links back to
    // that appointment when it does (unique: one invoice per visit).
    // Charges with no booked visit behind them -- a lab ordered at the
    // desk, a pharmacy sale -- carry no link; as with medical records,
    // the link is provenance, not a precondition.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", unique = true)
    private Appointment appointment;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(length = 1000)
    private String notes;

    // Charge lines keep the order they were written in; the list is
    // replaced as a whole when an unpaid invoice is corrected.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "invoice_charge_items",
            joinColumns = @JoinColumn(name = "invoice_id"))
    @OrderColumn(name = "line_order")
    private List<ChargeItem> lines = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    // Set when the invoice moves to PAID, null in every other state.
    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Invoice() {
    }

    public Invoice(Patient patient, Appointment appointment, Instant issuedAt,
                   String notes, List<ChargeItem> lines) {
        apply(patient, appointment, issuedAt, notes, lines);
    }

    public void apply(Patient patient, Appointment appointment, Instant issuedAt,
                      String notes, List<ChargeItem> lines) {
        this.patient = patient;
        this.appointment = appointment;
        this.issuedAt = issuedAt;
        this.notes = notes;
        this.lines = new ArrayList<>(lines);
    }

    public void changeStatus(InvoiceStatus status) {
        this.status = status;
        this.paidAt = status == InvoiceStatus.PAID ? Instant.now() : null;
    }

    // The total is derived from the lines, never stored and never taken
    // from the request, so it cannot disagree with what was charged.
    public long getTotalCents() {
        return lines.stream().mapToLong(ChargeItem::getLineTotalCents).sum();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public Appointment getAppointment() { return appointment; }
    public Instant getIssuedAt() { return issuedAt; }
    public String getNotes() { return notes; }
    public List<ChargeItem> getLines() { return lines; }
    public InvoiceStatus getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
