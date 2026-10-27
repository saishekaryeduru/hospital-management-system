package com.saishekar.hospital.model;

// How serious a recorded allergy is. UNKNOWN is a real chart state,
// not a placeholder: a patient may know a substance harms them without
// knowing how the reaction was graded, and migrated free-text notes
// land there rather than being assigned a severity nobody recorded.
public enum AllergySeverity {
    MILD,
    MODERATE,
    SEVERE,
    LIFE_THREATENING,
    UNKNOWN
}
