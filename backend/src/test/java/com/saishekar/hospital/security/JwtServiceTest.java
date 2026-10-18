package com.saishekar.hospital.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwt = new JwtService("test-secret-test-secret-test-secret-32", 60_000);

    @Test
    void tokenRoundTripsEmailAndRole() {
        String token = jwt.generateToken("doctor@hospital.test", "DOCTOR");
        assertTrue(jwt.isValid(token));
        assertEquals("doctor@hospital.test", jwt.extractEmail(token));
        assertEquals("DOCTOR", jwt.extractRole(token));
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwt.generateToken("doctor@hospital.test", "DOCTOR");
        assertFalse(jwt.isValid(token + "x"));
    }
}
