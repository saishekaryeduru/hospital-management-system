package com.saishekar.hospital.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

// One substance a patient is allergic to: what it is, what it does to
// them, and how serious that is. An entry has no identity of its own --
// it is written, read, and replaced as part of its patient record --
// so it is an embeddable owned by the patient rather than an entity
// with its own lifecycle, the same treatment prescription lines get.
// The reaction is optional because a patient can report the substance
// without recalling the reaction; the severity is always recorded,
// with UNKNOWN for exactly that case.
@Embeddable
public class Allergy {

    @Column(nullable = false, length = 120)
    private String substance;

    @Column(length = 200)
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AllergySeverity severity;

    protected Allergy() {
    }

    public Allergy(String substance, String reaction, AllergySeverity severity) {
        this.substance = substance;
        this.reaction = reaction;
        this.severity = severity;
    }

    public String getSubstance() { return substance; }
    public String getReaction() { return reaction; }
    public AllergySeverity getSeverity() { return severity; }
}
