package com.saishekar.hospital.model;

// An appointment moves in one direction: it is booked, the patient either
// arrives (CHECKED_IN, then COMPLETED) or does not (NO_SHOW), and a booking
// that will not happen at all is CANCELLED. Terminal states free the slot:
// only SCHEDULED and CHECKED_IN appointments block a doctor's calendar.
public enum AppointmentStatus {
    SCHEDULED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
