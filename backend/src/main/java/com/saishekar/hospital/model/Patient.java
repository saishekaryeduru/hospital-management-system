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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private String phone;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", nullable = false)
    private BloodGroup bloodGroup;

    private String address;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    // Structured allergy entries keep the order they were written in;
    // the list is replaced as a whole when the record is corrected, so
    // an allergy a patient no longer reports actually leaves the chart.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "patient_allergies",
            joinColumns = @JoinColumn(name = "patient_id"))
    @OrderColumn(name = "line_order")
    private List<Allergy> allergies = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Patient() {
    }

    public Patient(String firstName, String lastName, LocalDate dateOfBirth, Gender gender,
                   String phone, String email, BloodGroup bloodGroup, String address,
                   String emergencyContactName, String emergencyContactPhone, List<Allergy> allergies) {
        apply(firstName, lastName, dateOfBirth, gender, phone, email, bloodGroup, address,
                emergencyContactName, emergencyContactPhone, allergies);
    }

    public void apply(String firstName, String lastName, LocalDate dateOfBirth, Gender gender,
                      String phone, String email, BloodGroup bloodGroup, String address,
                      String emergencyContactName, String emergencyContactPhone, List<Allergy> allergies) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.phone = phone;
        this.email = email;
        this.bloodGroup = bloodGroup;
        this.address = address;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
        this.allergies = allergies == null ? new ArrayList<>() : new ArrayList<>(allergies);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public Gender getGender() { return gender; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public BloodGroup getBloodGroup() { return bloodGroup; }
    public String getAddress() { return address; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public List<Allergy> getAllergies() { return allergies; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
