package com.saishekar.hospital.model;

// Staff roles drive authorization. Registration accepts a role because a
// hospital system is provisioned by an administrator in practice; public
// self-service sign-up would only ever create a pending account.
public enum Role {
    ADMIN,
    DOCTOR,
    NURSE,
    RECEPTIONIST
}
