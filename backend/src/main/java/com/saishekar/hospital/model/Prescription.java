package com.saishekar.hospital.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// One line on a visit's prescription: what was prescribed and how it is
// taken. A line has no identity of its own -- it is written, read, and
// replaced as part of its medical record -- so it is an embeddable owned
// by the record rather than an entity with its own lifecycle.
@Embeddable
public class Prescription {

    @Column(nullable = false, length = 120)
    private String medication;

    @Column(nullable = false, length = 80)
    private String dosage;

    @Column(nullable = false, length = 80)
    private String frequency;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(length = 300)
    private String instructions;

    protected Prescription() {
    }

    public Prescription(String medication, String dosage, String frequency,
                        Integer durationDays, String instructions) {
        this.medication = medication;
        this.dosage = dosage;
        this.frequency = frequency;
        this.durationDays = durationDays;
        this.instructions = instructions;
    }

    public String getMedication() { return medication; }
    public String getDosage() { return dosage; }
    public String getFrequency() { return frequency; }
    public Integer getDurationDays() { return durationDays; }
    public String getInstructions() { return instructions; }
}
